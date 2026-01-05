#!/usr/bin/env python3
import requests
import json

def test_error_check():
    # 测试错误检查API
    url = "http://localhost:8080/check-errors"
    
    # 测试我们创建的包含错误的类
    params = {"class": "TestErrorCheck"}
    
    try:
        response = requests.get(url, params=params)
        print(f"状态码: {response.status_code}")
        print(f"响应内容:")
        
        if response.headers.get('content-type', '').startswith('application/json'):
            result = response.json()
            print(json.dumps(result, indent=2, ensure_ascii=False))
            
            if result.get('success') and result.get('data'):
                print("\n=== 错误检查结果 ===")
                print(result['data'])
        else:
            print(response.text)
            
    except requests.exceptions.ConnectionError:
        print("无法连接到服务器，请确保 JBCall 服务正在运行")
    except Exception as e:
        print(f"请求失败: {e}")

if __name__ == "__main__":
    test_error_check()