import sqlite3
import os

print("开始测试...")

# 获取当前脚本所在的绝对路径
base_dir = os.path.dirname(os.path.abspath(__file__))
db_path = os.path.join(base_dir, "test.db")

print(f"数据库将保存在: {db_path}")

try:
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    
    cur.execute("CREATE TABLE IF NOT EXISTS user (id INTEGER PRIMARY KEY, name TEXT)")
    cur.execute("INSERT INTO user (name) VALUES (?)", ("Alice",))
    conn.commit()
    
    cur.execute("SELECT id, name FROM user")
    print("查询结果:", cur.fetchall())
    
    conn.close()
    print(f"文件是否生成: {os.path.exists(db_path)}")
    print("测试成功！")
except Exception as e:
    print("发生错误:", e)