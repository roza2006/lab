package com.medical.platform.circulation.domain;

/**
 * 流转单号生成端口。聚合只接收生成好的编号。
 */
public interface CirculationNoGenerator {

    String next();
}
