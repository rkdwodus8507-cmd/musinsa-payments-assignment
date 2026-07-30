package com.musinsa.payments.point.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.musinsa.payments.point.service.dto.EarnCommand;
import com.musinsa.payments.point.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("감사 로그 - 잔액이 바뀐 거래만 남는다")
class PointAuditLogTest extends IntegrationTestSupport {

    private static final String REQUEST_KEY = "req-audit-1";

    private Logger auditRecorder;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        auditRecorder = (Logger) LoggerFactory.getLogger("point-audit");
        auditRecorder.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        auditRecorder.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditRecorder.detachAppender(appender);
    }

    @Test
    @DisplayName("적립 / 사용 / 사용취소마다 잔액이 함께 기록된다")
    void everyMutationIsRecorded() {
        earn(1000, null);
        var use = use("ORDER-1", 400);
        cancelUse(use.getPointKey(), 100);

        assertThat(auditLines())
                .hasSize(3)
                .anySatisfy(line -> assertThat(line).contains("type=EARN").contains("balanceAfter=1000"))
                .anySatisfy(line -> assertThat(line).contains("type=USE").contains("orderId=ORDER-1").contains("balanceAfter=600"))
                .anySatisfy(line -> assertThat(line).contains("type=USE_CANCEL").contains("balanceAfter=700"));
    }

    @Test
    @DisplayName("재전송된 요청은 변경 기록 없이 duplicate 로만 남는다")
    void duplicateRequestIsRecordedSeparately() {
        earnService.earn(EarnCommand.ofUser(USER_ID, 1000, null, null, REQUEST_KEY));
        earnService.earn(EarnCommand.ofUser(USER_ID, 1000, null, null, REQUEST_KEY));

        List<String> lines = auditLines();

        assertThat(lines).hasSize(2);
        assertThat(lines).filteredOn(line -> line.contains("duplicate=true")).hasSize(1);
        assertThat(lines).filteredOn(line -> line.contains("balanceAfter=")).hasSize(1);
        assertThat(balanceOf(USER_ID)).isEqualTo(1000);
    }

    @Test
    @DisplayName("requestKey 와 pointKey 가 함께 남아 사후 추적이 가능하다")
    void auditLineCarriesKeys() {
        String pointKey = earnService.earn(
                EarnCommand.ofUser(USER_ID, 500, null, null, REQUEST_KEY)).getPointKey();

        assertThat(auditLines()).singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("pointKey=" + pointKey)
                        .contains("requestKey=" + REQUEST_KEY)
                        .contains("userId=" + USER_ID));
    }

    private List<String> auditLines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
