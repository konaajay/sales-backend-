package com.lms.www.leadmanagement.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class PaymentRedirectController {

    @Value("${app.frontend-url:https://salestoo.netlify.app}")
    private String frontendUrl;

    @GetMapping("/pay/{sessionId}")
    public RedirectView redirectToFrontend(@PathVariable String sessionId,
            @RequestParam(required = false) String mode) {
        String redirectUrl = frontendUrl + "/pay/" + sessionId;
        if (mode != null) {
            redirectUrl += "?mode=" + mode;
        }
        System.out.println(">>> REDIRECTING /pay/ request from Backend to: " + redirectUrl);
        return new RedirectView(redirectUrl);
    }
}
