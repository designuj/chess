package dev.autowired.chess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ViewController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/game/{gameId}")
    public String game(@PathVariable String gameId, Model model) {
        model.addAttribute("gameId", gameId);
        return "game";
    }
}