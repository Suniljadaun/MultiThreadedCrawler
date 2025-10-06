package com.sunil.finintel.portfolio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sunil.finintel.common.NotFoundException;

@WebMvcTest(controllers = {PortfolioController.class, MarketPriceController.class})
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @MockitoBean
    private MarketPriceService marketPriceService;

    @Test
    void returnsPortfolio() throws Exception {
        PositionView acme = new PositionView("ACME", 10, new BigDecimal("100.0000"), new BigDecimal("112.5000"),
                new BigDecimal("1125.0000"), new BigDecimal("125.0000"));
        when(portfolioService.getPortfolio(1L)).thenReturn(new PortfolioResponse(1L, List.of(acme),
                new BigDecimal("1000.0000"), new BigDecimal("1125.0000"), new BigDecimal("125.0000")));

        mockMvc.perform(get("/api/v1/portfolio/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions[0].symbol").value("ACME"))
                .andExpect(jsonPath("$.positions[0].quantity").value(10))
                .andExpect(jsonPath("$.totalUnrealizedPnl").value(125.0));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        when(portfolioService.getPortfolio(9L)).thenThrow(new NotFoundException("user 9 not found"));

        mockMvc.perform(get("/api/v1/portfolio/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePriceReturnsNewPrice() throws Exception {
        when(marketPriceService.update(eq("ACME"), any()))
                .thenReturn(new MarketPriceResponse("ACME", new BigDecimal("120.0000"), Instant.now()));

        mockMvc.perform(put("/api/v1/market-prices/ACME")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(120.0));
    }

    @Test
    void negativePriceIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/market-prices/ACME")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
