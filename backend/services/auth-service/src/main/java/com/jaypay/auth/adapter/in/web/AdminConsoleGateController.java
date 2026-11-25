package com.jaypay.auth.adapter.in.web;

import com.jaypay.common.WebAdapter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebAdapter
@RestController
class AdminConsoleGateController {

    @RequestMapping("/authorize/**")
    ResponseEntity<Void> allow() {
        return ResponseEntity.noContent().build();
    }
}
