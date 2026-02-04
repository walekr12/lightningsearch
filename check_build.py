#!/usr/bin/env python3
"""
GitHub Actions 构建状态监控脚本
每3分钟检查一次构建状态，通过企业微信webhook发送通知
"""

import subprocess
import requests
import time
import json

WEBHOOK_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=cfee5a94-c8cb-4295-a4fd-63e0c813073a"
CHECK_INTERVAL = 180  # 3分钟

def get_build_status():
    """获取最新的GitHub Actions构建状态"""
    try:
        result = subprocess.run(
            ["gh", "run", "list", "--limit", "1", "--json", "status,conclusion,name,headBranch,displayTitle,createdAt,updatedAt"],
            capture_output=True,
            text=True,
            cwd=r"E:\xunlei\lightningsearch"
        )
        if result.returncode == 0:
            runs = json.loads(result.stdout)
            if runs:
                return runs[0]
    except Exception as e:
        print(f"获取状态失败: {e}")
    return None

def send_wechat_message(content):
    """发送企业微信消息"""
    data = {
        "msgtype": "markdown",
        "markdown": {
            "content": content
        }
    }
    try:
        response = requests.post(WEBHOOK_URL, json=data)
        return response.status_code == 200
    except Exception as e:
        print(f"发送消息失败: {e}")
        return False

def format_status_message(run_info):
    """格式化状态消息"""
    status = run_info.get("status", "unknown")
    conclusion = run_info.get("conclusion", "")
    title = run_info.get("displayTitle", "Unknown")
    branch = run_info.get("headBranch", "unknown")

    if status == "completed":
        if conclusion == "success":
            emoji = "✅"
            status_text = "构建成功"
            color = "info"
        else:
            emoji = "❌"
            status_text = f"构建失败 ({conclusion})"
            color = "warning"
    elif status == "in_progress":
        emoji = "🔄"
        status_text = "构建中..."
        color = "comment"
    else:
        emoji = "⏳"
        status_text = status
        color = "comment"

    message = f"""**Lightning Search 构建状态**
> {emoji} <font color="{color}">{status_text}</font>
>
> **提交:** {title}
> **分支:** {branch}
> **时间:** {run_info.get('updatedAt', 'N/A')}"""

    return message, status == "completed"

def main():
    print("开始监控 GitHub Actions 构建状态...")
    print(f"每 {CHECK_INTERVAL} 秒检查一次")
    print("按 Ctrl+C 停止\n")

    last_status = None

    while True:
        run_info = get_build_status()

        if run_info:
            current_status = f"{run_info.get('status')}_{run_info.get('conclusion')}"

            # 状态变化时发送通知
            if current_status != last_status:
                message, is_completed = format_status_message(run_info)
                print(f"[{time.strftime('%H:%M:%S')}] 发送状态通知...")
                if send_wechat_message(message):
                    print("  -> 发送成功")
                else:
                    print("  -> 发送失败")

                last_status = current_status

                # 如果构建完成，停止监控
                if is_completed:
                    print("\n构建已完成，停止监控")
                    break
            else:
                print(f"[{time.strftime('%H:%M:%S')}] 状态未变化: {run_info.get('status')}")
        else:
            print(f"[{time.strftime('%H:%M:%S')}] 无法获取构建状态")

        time.sleep(CHECK_INTERVAL)

if __name__ == "__main__":
    main()
