package com.ecommerce.common.contracts;

import java.math.BigDecimal;

public record OrderLine(String productId, int quantity, BigDecimal unitPrice) {
}
