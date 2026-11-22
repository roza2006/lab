package com.medical.platform.prescription.infrastructure.id;

import com.medical.platform.prescription.domain.RxNoGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内处方号：RX + 日期 + 6 位序号。单实例部署够用，多实例需换成数据库序列。
 */
@Component
public class DailyRxNoGenerator implements RxNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AtomicInteger sequence = new AtomicInteger(
            (int) (System.currentTimeMillis() % 1_000_000));

    @Override
    public String next() {
        int no = sequence.updateAndGet(current -> current >= 999_999 ? 1 : current + 1);
        return "RX" + LocalDate.now().format(DAY) + String.format("%06d", no);
    }
}
