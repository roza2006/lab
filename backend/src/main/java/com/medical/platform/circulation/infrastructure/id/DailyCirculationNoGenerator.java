package com.medical.platform.circulation.infrastructure.id;

import com.medical.platform.circulation.domain.CirculationNoGenerator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内流转单号：C + 日期 + 6 位序号。单实例够用，多实例需换数据库序列。
 */
@Component
public class DailyCirculationNoGenerator implements CirculationNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AtomicInteger sequence = new AtomicInteger(
            (int) (System.currentTimeMillis() % 1_000_000));

    @Override
    public String next() {
        int no = sequence.updateAndGet(current -> current >= 999_999 ? 1 : current + 1);
        return "C" + LocalDate.now().format(DAY) + String.format("%06d", no);
    }
}
