package com.novinkish.peygir.web;

import com.novinkish.peygir.service.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** خطاهای مجاز (BusinessException) را به صورت پیام فارسی در همان صفحه نمایش می‌دهد. */
@ControllerAdvice
public class ErrorAdvice {
    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, HttpServletRequest req, RedirectAttributes ra) {
        ra.addFlashAttribute("error", e.getMessage());
        String ref = req.getHeader("Referer");
        String base = req.getScheme() + "://" + req.getHeader("Host");
        return "redirect:" + (ref != null && ref.startsWith(base) ? ref : "/");
    }
}
