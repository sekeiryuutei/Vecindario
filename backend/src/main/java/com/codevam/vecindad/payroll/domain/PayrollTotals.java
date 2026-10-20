package com.codevam.vecindad.payroll.domain;

import java.math.BigDecimal;

public record PayrollTotals(BigDecimal gross, BigDecimal deductions, BigDecimal net, BigDecimal employerCost) {
}
