import json
import clip
import torch
from PIL import Image
import requests
from io import BytesIO

def embedding_image(url):
    device = "cpu"
    model, preprocess = clip.load("ViT-B/32", device=device)

    img = Image.open(BytesIO(requests.get(url).content)).convert("RGB")
    try:
        # 预处理 & 推理
        img_tensor = preprocess(img).unsqueeze(0).to(device)
        with torch.no_grad():
            img_feat = model.encode_image(img_tensor)
            img_feat = img_feat / img_feat.norm(dim=-1, keepdim=True)  # 归一化

        # 转成 list
        vector = img_feat.cpu().numpy().flatten().tolist()

        return json.dumps({"vector": vector})

    except Exception as e:
        # 返回空向量，让 Java 端判断
        return json.dumps({"vector": []})
    
import pika
# ================= 3. RabbitMQ 消息回调 =================
def on_message(channel, method, properties, body):
    try:
        # 1. 解析消息：url|||title
        message_str = body.decode("utf-8")
        parts = message_str.split("|||")

        if len(parts) < 2:
            print(f"⚠️ 消息格式错误: {message_str}")
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        title = parts[1]
        url = parts[0]
        print(f"📥 收到任务: {title}")

        # 2. 计算向量
        vector = embedding_image(url)
        if not vector:
            print(f"⚠️ 向量计算失败: {title}")
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        # 3. 构造返回结果
        result = {
            "title": title,
            "url": url,
            "vector": vector
        }

        # 4. 发送到 vectorResult 队列
        result_queue = "vectorResult"
        channel.queue_declare(queue=result_queue, durable=True)

        channel.basic_publish(
            exchange="",
            routing_key=result_queue,
            body=json.dumps(result, ensure_ascii=False),
            properties=pika.BasicProperties(
                delivery_mode=2  # 持久化
            )
        )

        print(f"📤 已发送结果: {title} | 向量维度: {len(vector)}")

        # 5. 手动 ACK
        channel.basic_ack(delivery_tag=method.delivery_tag)

    except Exception as e:
        print(f"❌ 处理消息异常: {e}")
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

def main():
    # RabbitMQ 连接参数（根据你的 RabbitMQ 配置修改）
    rabbitmq_host = "192.168.253.128"   # RabbitMQ 服务器地址
    rabbitmq_port = 5672          # 默认端口
    rabbitmq_user = "guest"       # 用户名（默认 guest）
    rabbitmq_pass = "guest"       # 密码（默认 guest）
    queue_name = "sendUrl"        # 要监听的队列名

    # 建立连接
    credentials = pika.PlainCredentials(rabbitmq_user, rabbitmq_pass)
    connection = pika.BlockingConnection(
        pika.ConnectionParameters(
            host=rabbitmq_host,
            port=rabbitmq_port,
            credentials=credentials,
        )
    )
    channel = connection.channel()

    # 声明队列（确保队列存在，durable=True 表示队列持久化）
    channel.queue_declare(queue=queue_name, durable=True)

    # 设置 QoS（每次只处理一条消息，避免内存溢出）
    channel.basic_qos(prefetch_count=1)

    # 消费消息（auto_ack=False 表示手动确认）
    channel.basic_consume(
        queue=queue_name,
        on_message_callback=on_message,
        auto_ack=False,
    )

    print(f" [*] 等待队列 {queue_name} 的消息。按 CTRL+C 退出")
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        print(" [*] 停止消费")
        channel.stop_consuming()
    finally:
        connection.close()

if __name__ == "__main__":
    main()
