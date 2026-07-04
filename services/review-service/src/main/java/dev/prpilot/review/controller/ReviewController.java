package dev.prpilot.review.controller;

import dev.prpilot.review.model.Review;
import dev.prpilot.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {

    private final ReviewRepository reviewRepository;

    @GetMapping
    public List<Review> getReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviewRepository.findAll(
                PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @GetMapping("/{id}")
    public Review getReview(@PathVariable java.util.UUID id) {
        return reviewRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Review not found"));
    }
}