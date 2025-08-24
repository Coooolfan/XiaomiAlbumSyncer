import requests
import json
from pprint import pprint
import base64
import time

def display_image(content):
    # Display the image in the terminal using the Inline Image Protocol
    # https://iterm2.com/documentation-images.html
    base64_content = base64.b64encode(content).decode('utf-8')
    
    # 保存QR码图片到文件作为备用
    with open("qr_code.png", "wb") as f:
        f.write(content)
    
    # 尝试多种方式显示QR码
    try:
        # iTerm2 inline image protocol
        print("\033]1337;File=inline=1;width=240px;preserveAspectRatio=1:{}\x07".format(base64_content))
        print("\nQR码已显示在终端中（如果支持iTerm2）")
    except:
        pass
    
    # 如果终端不支持图片，提供文件路径
    print("\n如果终端没有显示QR码，请查看文件: qr_code.png")
    print("或者使用以下命令查看: open qr_code.png")


session = requests.Session()

print("Starting QR code login...")

data = {
    '_qrsize': '240', 
    'qs': '%3Fsid%3Dxiaomiio%26_json%3Dtrue', 
    'callback': "https://sts.api.io.mi.com/sts", 
    '_hasLogo': 'false', 
    'sid': 'i.mi.com', 
    'serviceParam': '', 
    '_locale': 'pl_PL',
    '_dc': str(int(time.time() * 1000))
}
response = session.get(
    'https://account.xiaomi.com/longPolling/loginUrl',
    params=data,
)

response_data = json.loads(response.text.replace("&&&START&&&", ""))

pprint(response_data)

qr_image_url = response_data['qr']

response = session.get(qr_image_url)
image_content = response.content
display_image(image_content)

print("QR code displayed. Please scan it with your Xiaomi device.")
print("Waiting for login...")
long_polling_url = response_data['lp']
timeout = response_data['timeout']

print("Long polling URL: " + long_polling_url)
start_time = time.time()
login_success = False

# Start long polling
while True:
    try:
        response = session.get(long_polling_url, timeout=30)
        
        if response.status_code == 200:
            response_text = response.text.strip()
            print(f"Response: {response_text}")
            
            if response_text and not response_text.startswith("&&&START&&&{\"code\":70016}"):
                # 移除可能的 &&&START&&& 前缀
                if response_text.startswith("&&&START&&&"):
                    response_text = response_text.replace("&&&START&&&", "")
                
                try:
                    response_data = json.loads(response_text)
                    if response_data.get('code') == 0 and 'userId' in response_data:
                        print("Login successful!")
                        login_success = True
                        break
                    elif response_data.get('code') == 70016:
                        print("QR code expired or not scanned yet...")
                    else:
                        print(f"Unexpected response: {response_data}")
                except json.JSONDecodeError:
                    print(f"Failed to parse JSON response: {response_text}")
            else:
                print("Waiting for QR code scan...")
        else:
            print(f"Long polling failed with status {response.status_code}")
            
    except requests.exceptions.Timeout:
        print("Long polling timed out, retrying...")
        if time.time() - start_time > timeout:
            print("Long polling timed out after {} seconds.".format(timeout))
            break
    except requests.exceptions.RequestException as e:
        print(f"An error occurred: {e}")
        break
    
    # 检查是否超时
    if time.time() - start_time > timeout:
        print("Login timeout! QR code expired.")
        break
        
    # 添加短暂延迟
    time.sleep(3)

if not login_success:
    print("Login failed! Please try again.")
    exit(1)

if response.status_code != 200:
    print("Long polling failed with status code: " + str(response.status_code))
    exit(1)

print("Login successful!")
print("Response data:")

response_data = json.loads(response.text.replace("&&&START&&&", ""))
pprint(response_data)

user_id = response_data['userId']
ssecurity = response_data['ssecurity']
cuser_id = response_data['cUserId']
pass_token = response_data['passToken']
location = response_data['location']
print("User ID: " + str(user_id))
print("Ssecurity: " + str(ssecurity))
print("CUser ID: " + str(cuser_id))
print("Pass token: " + str(pass_token))
print()
print("Fetching service token...")

if not location:
    print("No location found in response data.")
    exit(1)

print("Fetching service token...")
location = response_data['location']
response = session.get(location, headers={'content-type': 'application/x-www-form-urlencoded'})
if response.status_code != 200:
    print("Failed to fetch service token")
    exit(1)

service_token = response.cookies['serviceToken']
print("Service token: " + str(service_token))

from micloud import MiCloud

mc = MiCloud(None, None)
mc.user_id =  user_id
mc.service_token = service_token
mc.session = None
mc.ssecurity = ssecurity
mc.cuser_id = cuser_id
mc.pass_token = pass_token

devices = mc.get_devices()
print("Devices:")
print(json.dumps(devices, indent=2, sort_keys=True))
print("Done.")
