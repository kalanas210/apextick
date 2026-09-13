package com.apextick.booking.payment;

import com.apextick.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class PaymentConfigApiTest {

    @Autowired MockMvc mvc;

    /** The storefront's demo label and its "no real money is charged" line come from this answer and nowhere else. */
    @Test
    void anyone_can_ask_whether_money_is_real_and_whether_the_fixtures_are_a_demo() throws Exception {
        mvc.perform(get("/api/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.testMode").value(true))
                .andExpect(jsonPath("$.demo").value(true));
    }
}
