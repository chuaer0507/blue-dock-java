# BlueDock — Maven / deploy/scripts 薄封装；复杂逻辑仍在脚本内。
# 用法：make help

.DEFAULT_GOAL := help

BASE_URL           ?= http://localhost:18080
MVN                ?= mvn

# 发布：TAG 默认 git describe；推送 / K8s 切换需 BLUEDOCK_REGISTRY；Compose 切换不需
TAG                ?= $(shell bash deploy/scripts/resolve-image-tag.sh 2>/dev/null || echo dev-unknown)
BLUEDOCK_REGISTRY      ?=
DEPLOY_TARGET      ?= compose
K8S_NAMESPACE      ?= bluedock-prod

define require_registry
ifndef BLUEDOCK_REGISTRY
	$(error BLUEDOCK_REGISTRY is required, e.g. make $(1) BLUEDOCK_REGISTRY=ghcr.io/<owner>)
endif
endef

.PHONY: help compile test package package-workers test-module \
	dev-up dev dev-restart dev-boot dev-workers \
	compose-up githooks smoke k8s-check fmt sync-env-tag \
	run-boot run-worker-notify run-worker-index \
	image-build image-push release prod-switch prod-deploy prod-rollback

##@ 构建

help: ## 列出常用命令
	@awk 'BEGIN {FS = ":.*##"; printf "\n用法: make <target>\n"} \
		/^##@/ { printf "\n%s\n", substr($$0, 5) } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  %-24s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@printf "\n示例: make test-module MODULE=bluedock-project\n"
	@printf "      make release BLUEDOCK_REGISTRY=ghcr.io/<owner>\n"
	@printf "      make prod-switch TAG=$(TAG)\n"
	@printf "      make prod-switch DEPLOY_TARGET=k8s BLUEDOCK_REGISTRY=ghcr.io/<owner> TAG=$(TAG)\n\n"

compile: ## mvn clean compile
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 compile"; else $(MVN) clean compile; fi

fmt: ## 按 .editorconfig 机械格式化（LF / 末尾换行 / 裁剪行尾空白）
	python3 scripts/apply-editorconfig.py

test: ## mvn test
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 test"; else $(MVN) test; fi

package: ## 打包 bluedock-boot JAR
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 package"; else $(MVN) clean package; fi

package-workers: ## 打包 Worker JAR（跳过测试）
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 package-workers"; else $(MVN) -pl bluedock-worker-notify,bluedock-worker-index -am package -DskipTests; fi

test-module: ## 单模块测试（MODULE=bluedock-project）
ifndef MODULE
	$(error MODULE is required, e.g. make test-module MODULE=bluedock-project)
endif
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 test-module"; else $(MVN) -pl $(MODULE) -am test; fi

##@ 本地开发

dev-up: ## 启动 Docker 依赖栈（MySQL / Redis / Kafka / Nginx）
	bash deploy/scripts/dev-up.sh

dev: ## 后台启动 boot + Workers（健康则跳过）
	bash deploy/scripts/dev-apps.sh

dev-restart: ## 先停再启 boot + Workers（改代码后推荐）
	bash deploy/scripts/dev-apps.sh --restart

dev-boot: ## 仅后台启动 bluedock-boot
	bash deploy/scripts/dev-boot.sh

dev-workers: ## 仅后台启动 Workers
	bash deploy/scripts/dev-workers.sh

run-boot: ## 前台 spring-boot:run（bluedock-boot）
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 run-boot"; else $(MVN) -pl bluedock-boot -am spring-boot:run; fi

run-worker-notify: ## 前台 spring-boot:run（worker-notify）
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 run-worker-notify"; else $(MVN) -pl bluedock-worker-notify -am spring-boot:run; fi

run-worker-index: ## 前台 spring-boot:run（worker-index）
	@if [[ ! -f pom.xml ]]; then echo "make: 尚无 pom.xml，跳过 run-worker-index"; else $(MVN) -pl bluedock-worker-index -am spring-boot:run; fi

compose-up: ## 联调镜像栈（按 .env.dev 的 TAG 构建；先 sync-env-tag）
	bash deploy/scripts/sync-env-image-tag.sh $(TAG)
	docker compose -f deploy/docker-compose.yml --env-file deploy/.env.dev up -d --build

githooks: ## 启用本地 commit-msg 钩子
	bash scripts/setup_githooks.sh

##@ 验收

smoke: ## 核心 API smoke（BASE_URL，默认 Nginx :18080）
	BASE_URL=$(BASE_URL) bash deploy/scripts/staging-core-smoke.sh

k8s-check: ## K8s manifest 无集群预检（CI 同脚本）
	bash deploy/scripts/k8s-manifest-check.sh

##@ 发布（推送 / K8s 需 BLUEDOCK_REGISTRY；Compose 切换仅需 TAG）

sync-env-tag: ## 按 git tag 写入 deploy/.env.* 的 BLUEDOCK_VERSION（镜像 tag 同值）
	bash deploy/scripts/sync-env-image-tag.sh $(TAG)

image-build: ## 构建 boot + Worker 镜像（并回写 BLUEDOCK_VERSION）
	BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY) bash deploy/scripts/image-build.sh $(TAG)

image-push: release-push ## 推送镜像到 Registry

release-push:
	$(call require_registry,image-push)
	BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY) bash deploy/scripts/image-push.sh $(TAG)

release: ## 构建并推送镜像（本地 / CI；不切换生产）
	$(call require_registry,release)
	$(MAKE) image-build TAG=$(TAG) BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY)
	$(MAKE) release-push TAG=$(TAG) BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY)

prod-switch: ## 生产切换线上镜像（Compose 默认；K8s 见 DEPLOY_TARGET）
ifeq ($(DEPLOY_TARGET),k8s)
	$(call require_registry,prod-switch)
endif
	BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY) bash deploy/scripts/prod-switch.sh $(TAG) \
		--target $(DEPLOY_TARGET) \
		$(if $(filter k8s,$(DEPLOY_TARGET)),--namespace $(K8S_NAMESPACE),)

prod-deploy: ## 构建 + 推送 + 切换（切换在生产机执行）
	$(call require_registry,prod-deploy)
	$(MAKE) release TAG=$(TAG) BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY)
	$(MAKE) prod-switch TAG=$(TAG) BLUEDOCK_REGISTRY=$(BLUEDOCK_REGISTRY) \
		DEPLOY_TARGET=$(DEPLOY_TARGET) K8S_NAMESPACE=$(K8S_NAMESPACE)

prod-rollback: ## 生产回滚上一版本（可选 TAG=…）
	bash deploy/scripts/prod-rollback.sh $(if $(TAG),$(TAG),)
