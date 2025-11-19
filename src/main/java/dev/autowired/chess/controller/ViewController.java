package dev.autowired.chess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class ViewController {

    @GetMapping("/")
    String index() {
        return "index";
    }

    @GetMapping("/game")
    String game() {
        return "game";
    }
}