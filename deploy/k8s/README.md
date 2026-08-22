# Kubernetes manifests (kustomize)

无集群预检（推荐）：

```bash
bash deploy/scripts/k8s-manifest-check.sh
```

预览：

```bash
kubectl kustomize deploy/k8s/overlays/staging
kubectl kustomize deploy/k8s/overlays/prod
```

部署（先创建 Secret）：

```bash
cp deploy/k8s/base/secret.yaml.example /tmp/bluedock-app-secrets.yaml  # 填真实值
kubectl apply -f /tmp/bluedock-app-secrets.yaml -n bluedock-staging
kubectl apply -k deploy/k8s/overlays/staging
```

生产切换镜像（不在服务器 build）：

```bash
BLUEDOCK_REGISTRY=registry.example.com/bluedock bash deploy/scripts/prod-switch.sh 1.0.0-a1b2c3d --target k8s
```

回滚：

```bash
bash deploy/scripts/prod-rollback.sh --list
bash deploy/scripts/prod-rollback.sh --target k8s
```

说明：

- MySQL / Redis / Kafka 假定在独立 namespace（如 `bluedock-infra`），经 Secret / ConfigMap 注入
- 上传 PVC `bluedock-uploads` 为 RWO；多副本 boot 须 RWX 或改 OSS
- 详见 [docs/ops/deployment.md](../../docs/ops/deployment.md)
