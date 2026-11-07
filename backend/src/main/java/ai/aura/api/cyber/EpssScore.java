package ai.aura.api.cyber;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EpssScore(String cve, BigDecimal score, BigDecimal percentile, LocalDate date) {
}
