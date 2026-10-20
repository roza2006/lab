import numpy as np
import matplotlib.pyplot as plt

fs = 1000          # 采样率 1000 Hz
N = 1024           # 1024 个点
t = np.arange(N) / fs

# 构造一个信号：50Hz + 120Hz + 噪声
x = 0.5*np.sin(2*np.pi*50*t) + 0.2*np.sin(2*np.pi*120*t) + 0.1*np.random.randn(N)

# 加汉宁窗，做 FFT
X = np.fft.rfft(x * np.hanning(N))
freqs = np.fft.rfftfreq(N, 1/fs)
amp = np.abs(X) / (N/2)

plt.plot(freqs, amp)
plt.xlabel('频率 (Hz)')
plt.ylabel('幅值')
plt.show()