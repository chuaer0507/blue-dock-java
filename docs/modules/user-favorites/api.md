# 收藏 — API

| URL | 说明 | 状态 |
| --- | ---- | ---- |
| `GET api/users/favorites` | 分页列表；`type`=task/project/file/message | 已落地 |
| `POST api/users/favorite/toggle` | 切换收藏 | 已落地 |
| `POST api/users/favorite/remark` | 修改备注 | 已落地 |
| `POST api/users/favorites/clean` | 清理（可按 type） | 已落地 |
| `GET api/users/favorite/check` | 是否已收藏 | 已落地 |

表：`bluedock_user_favorites`（唯一 `(userId, fav_type, ref_id)`）。
