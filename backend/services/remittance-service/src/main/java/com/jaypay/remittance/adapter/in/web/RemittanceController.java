package com.jaypay.remittance.adapter.in.web;

import com.jaypay.common.WebAdapter;
import com.jaypay.remittance.application.port.in.FindRemittanceUseCase;
import com.jaypay.remittance.application.port.in.RequestRemittanceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@WebAdapter
@RestController
@RequiredArgsConstructor
public class RemittanceController {

    private final RemittanceCommands remittanceCommands;
    private final RequestRemittanceUseCase requestRemittanceUseCase;
    private final FindRemittanceUseCase findRemittanceUseCase;

    @PostMapping(path = "/remittance/request", consumes = MediaType.APPLICATION_JSON_VALUE)
    RemittanceDetail requestRemittance(@RequestBody RemittanceRequest request) {
        return RemittanceDetail.of(requestRemittanceUseCase.requestRemittance(remittanceCommands.parse(request)));
    }

    @GetMapping(path = "/remittance/{membershipId}")
    List<RemittanceDetail> findRemittanceHistory(@PathVariable String membershipId) {
        return findRemittanceUseCase.findRemittanceHistory(remittanceCommands.membershipId(membershipId)).stream()
                .map(RemittanceDetail::of)
                .toList();
    }
}
