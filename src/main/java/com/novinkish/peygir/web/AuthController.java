package com.novinkish.peygir.web;

import com.novinkish.peygir.domain.AppUser;
import com.novinkish.peygir.domain.Permission;
import com.novinkish.peygir.repository.UserRepository;
import com.novinkish.peygir.security.PeygirUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal PeygirUser u) {
        if (u.has(Permission.REPORT_CREATE) || u.has(Permission.TEAM_APPROVE)) return "redirect:/reports";
        if (u.has(Permission.VIEW_ALL) || u.has(Permission.TEAM_STATS)) return "redirect:/dashboard";
        if (u.has(Permission.ADMIN_PANEL)) return "redirect:/admin/users";
        return "redirect:/reports";
    }

    @GetMapping("/password")
    public String passwordForm() { return "password"; }

    @PostMapping("/password")
    @Transactional
    public String changePassword(@AuthenticationPrincipal PeygirUser u,
                                 @RequestParam String current,
                                 @RequestParam String password,
                                 @RequestParam String confirm,
                                 RedirectAttributes ra) {
        AppUser user = users.findById(u.getId()).orElseThrow();
        String err = null;
        if (!encoder.matches(current, user.getPasswordHash())) err = "رمز فعلی درست نیست";
        else if (password.length() < 6) err = "رمز جدید باید حداقل ۶ نویسه باشد";
        else if (!password.equals(confirm)) err = "تکرار رمز جدید با رمز جدید یکسان نیست";
        else if (password.equals(current)) err = "رمز جدید باید با رمز فعلی فرق داشته باشد";
        if (err != null) {
            ra.addFlashAttribute("error", err);
            return "redirect:/password";
        }
        user.setPasswordHash(encoder.encode(password));
        user.setMustChangePassword(false);
        ra.addFlashAttribute("success", "رمز عبور با موفقیت تغییر کرد");
        return "redirect:/";
    }
}
