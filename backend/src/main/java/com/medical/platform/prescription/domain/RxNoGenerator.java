package com.medical.platform.prescription.domain;

/**
 * 处方号生成端口。聚合只接收生成好的编号，不负责序列设施。
 */
public interface RxNoGenerator {

    String next();
}
