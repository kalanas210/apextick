package com.apextick.booking.payment;

import com.apextick.booking.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;
    private final PaymentProvider defaultProvider;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways, AppProperties props) {
        this.gateways = gateways;
        this.defaultProvider = PaymentProvider.valueOf(props.payment().provider().toUpperCase(Locale.ROOT));
    }

    public PaymentGateway get(PaymentProvider provider) {
        return gateways.stream().filter(g -> g.provider() == provider).findFirst()
                .orElseThrow(() -> new IllegalStateException("No payment gateway for provider " + provider));
    }

    public PaymentGateway defaultGateway() {
        return get(defaultProvider);
    }

    public PaymentProvider defaultProvider() {
        return defaultProvider;
    }
}
