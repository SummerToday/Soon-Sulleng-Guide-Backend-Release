package com.backend.soonsullengguide.controller;


import com.backend.soonsullengguide.config.JwtTokenProvider;
import com.backend.soonsullengguide.domain.Review;
import com.backend.soonsullengguide.domain.ReviewImage;
import com.backend.soonsullengguide.domain.User;
import com.backend.soonsullengguide.repository.UserRepository;
import com.backend.soonsullengguide.repository.ReviewRepository;
import com.backend.soonsullengguide.service.ReviewService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ReviewService reviewService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // 생성자에서 모든 의존성을 주입
    public ReviewController(ReviewRepository reviewRepository, ReviewService reviewService, JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewService = reviewService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }
    @PostMapping
    public ResponseEntity<String> submitReview(
            @RequestHeader("Authorization") String token, // 헤더에서 토큰을 받음
            @RequestParam("category") String category,
            @RequestParam("storeName") String storeName,
            @RequestParam("reviewTitle") String reviewTitle,
            @RequestParam("menuName") String menuName,
            @RequestParam("reviewContent") String reviewContent,
            @RequestParam("stars") int stars,
            @RequestParam("reviewDateTime") String reviewDateTime,
            @RequestParam("price") String price, // 가격 정보 추가
            @RequestParam("images") MultipartFile[] images) {

        // Bearer 토큰에서 "Bearer " 부분을 제거하고 실제 토큰만 추출
        String actualToken = token.replace("Bearer ", "");

        // 토큰에서 사용자 이메일 추출
        String email = jwtTokenProvider.getUsernameFromToken(actualToken);

        // 이메일로 DB에서 사용자 정보 조회
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(404).body("User not found.");
        }

        User user = optionalUser.get();
        Long userId = user.getId(); // 사용자의 user_id를 가져옴

        // 요청 파라미터를 로그로 출력하여 확인
        System.out.println("Category: " + category);
        System.out.println("Store Name: " + storeName);
        System.out.println("Review Title: " + reviewTitle);
        System.out.println("Menu Name: " + menuName);
        System.out.println("Review Content: " + reviewContent);
        System.out.println("Stars: " + stars);
        System.out.println("Review DateTime: " + reviewDateTime);
        System.out.println("Price: " + price); // 가격 로그 출력
        System.out.println("Number of Images: " + images.length);
        System.out.println("User ID: " + userId); // 유저 아이디 로그 출력

        // 리뷰 객체 생성 및 값 설정
        Review review = new Review();
        review.setCategory(category);
        review.setStoreName(storeName);
        review.setReviewTitle(reviewTitle);
        review.setMenuName(menuName);
        review.setReviewContent(reviewContent);
        review.setStars(stars);
        review.setReviewDateTime(LocalDateTime.parse(reviewDateTime)); // ISO 형식 시간 파싱
        review.setPrice(price); // 가격 정보 설정
        review.setUser(user); // 리뷰에 user_id 추가

        try {
            // 리뷰와 이미지를 저장
            reviewService.saveReview(review, images);
            return ResponseEntity.ok("Review submitted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error while submitting review: " + e.getMessage());
        }
    }
    @GetMapping("/getReviews")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getReviews(
            @RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");

        // 토큰에서 사용자 이메일 추출
        String email = jwtTokenProvider.getUsernameFromToken(actualToken);

        // 이메일로 사용자 정보 조회
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(404).body(Collections.emptyMap());
        }

        List<Review> reviews = reviewRepository.findAll();
        List<Map<String, Object>> foodReviews = new ArrayList<>();
        List<Map<String, Object>> dessertReviews = new ArrayList<>();

        // Android 에뮬레이터와 호환되는 서버 기본 URL 설정
        String baseUrl = "http://Soon-Sulleng-Guide-1365335815.ap-northeast-2.elb.amazonaws.com/images/"; // 로드 밸런서 주소로 저장.

        for (Review review : reviews) {
            Map<String, Object> reviewMap = new HashMap<>();
            reviewMap.put("id", review.getId());
            reviewMap.put("category", review.getCategory());
            reviewMap.put("storeName", review.getStoreName());
            reviewMap.put("reviewTitle", review.getReviewTitle());
            reviewMap.put("menuName", review.getMenuName());
            reviewMap.put("reviewContent", review.getReviewContent());
            reviewMap.put("stars", review.getStars());
            reviewMap.put("price", review.getPrice());
            reviewMap.put("reviewDateTime", review.getReviewDateTime().toString());

            // 첫 번째 이미지 경로 추가
            if (!review.getImages().isEmpty()) {
                String fullPath = review.getImages().get(0).getImagePath().replace("\\", "/");
                String basePath = "/home/ec2-user/static/images/"; // ec2 서버 내에 저장하도록 변경.
                // 해당 문자열이 포함된 위치를 찾고 이를 기준으로 잘라냄
                int startIndex = fullPath.indexOf(basePath);
                if (startIndex != -1) {
                    // basePath 이후의 경로를 잘라내어 uploads/~.jpg 형태로 반환
                    String imagePath = fullPath.substring(startIndex + basePath.length());
                    reviewMap.put("thumbnail", baseUrl + imagePath);
                } else {
                    reviewMap.put("thumbnail", fullPath); // basePath가 없을 경우 그대로 사용
                }
            } else {
                reviewMap.put("thumbnail", ""); // 이미지가 없을 경우 빈 문자열로 처리
            }

            // category에 따라 식당과 카페로 분류
            if ("식당".equals(review.getCategory())) {
                foodReviews.add(reviewMap);
            } else if ("카페".equals(review.getCategory())) {
                dessertReviews.add(reviewMap);
            }
        }

        // 분류한 리스트를 맵에 저장하여 반환
        Map<String, List<Map<String, Object>>> response = new HashMap<>();
        response.put("식당", foodReviews);
        response.put("카페", dessertReviews);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getReviewById(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        String actualToken = token.replace("Bearer ", "");

        // 토큰에서 사용자 이메일 추출
        String email = jwtTokenProvider.getUsernameFromToken(actualToken);

        // 이메일로 사용자 정보 조회
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(404).body(Collections.emptyMap());
        }

        Optional<Review> optionalReview = reviewRepository.findById(id);
        if (optionalReview.isEmpty()) {
            return ResponseEntity.status(404).body(Collections.emptyMap());
        }

        Review review = optionalReview.get();
        Map<String, Object> reviewMap = new HashMap<>();
        reviewMap.put("id", review.getId());
        reviewMap.put("category", review.getCategory());
        reviewMap.put("storeName", review.getStoreName());
        reviewMap.put("reviewTitle", review.getReviewTitle());
        reviewMap.put("menuName", review.getMenuName());
        reviewMap.put("reviewContent", review.getReviewContent());
        reviewMap.put("stars", review.getStars());
        reviewMap.put("price", review.getPrice());
        reviewMap.put("reviewDateTime", review.getReviewDateTime().toString());

        // 모든 이미지 경로 추가
        String baseUrl = "http://Soon-Sulleng-Guide-1365335815.ap-northeast-2.elb.amazonaws.com/images/"; // 로드 밸런서 주소로 저장.
        List<String> imagePaths = new ArrayList<>();
        for (ReviewImage image : review.getImages()) {
            String fullPath = image.getImagePath().replace("\\", "/");
            String basePath = "/home/ec2-user/static/images/"; // ec2 서버 내에 저장하도록 변경.
            int startIndex = fullPath.indexOf(basePath);
            if (startIndex != -1) {
                String imagePath = fullPath.substring(startIndex + basePath.length());
                imagePaths.add(baseUrl + imagePath);
            } else {
                imagePaths.add(fullPath);
            }
        }
        reviewMap.put("images", imagePaths);

        return ResponseEntity.ok(reviewMap);
    }

    @GetMapping("/allReviews")
    public ResponseEntity<List<Map<String, Object>>> getAllReviews(
            @RequestHeader("Authorization") String token) {
        String actualToken = token.replace("Bearer ", "");

        // 토큰에서 사용자 이메일 추출
        String email = jwtTokenProvider.getUsernameFromToken(actualToken);

        // 이메일로 사용자 정보 조회
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(404).body(Collections.emptyList());
        }

        // 최신순으로 정렬하여 모든 리뷰 가져오기 (`reviewDateTime`으로 정렬)
        List<Review> reviews = reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "reviewDateTime"));
        List<Map<String, Object>> reviewList = new ArrayList<>();

        // Android 에뮬레이터와 호환되는 서버 기본 URL 설정
        String baseUrl = "http://Soon-Sulleng-Guide-1365335815.ap-northeast-2.elb.amazonaws.com/images/";

        for (Review review : reviews) {
            Map<String, Object> reviewMap = new HashMap<>();
            reviewMap.put("id", review.getId());
            reviewMap.put("category", review.getCategory());
            reviewMap.put("storeName", review.getStoreName());
            reviewMap.put("reviewTitle", review.getReviewTitle());
            reviewMap.put("menuName", review.getMenuName());
            reviewMap.put("reviewContent", review.getReviewContent());
            reviewMap.put("stars", review.getStars());
            reviewMap.put("price", review.getPrice());
            reviewMap.put("reviewDateTime", review.getReviewDateTime().toString());

            // 첫 번째 이미지 경로 추가
            if (!review.getImages().isEmpty()) {
                String fullPath = review.getImages().get(0).getImagePath().replace("\\", "/");
                String basePath = "/home/ec2-user/static/images/";
                int startIndex = fullPath.indexOf(basePath);
                if (startIndex != -1) {
                    String imagePath = fullPath.substring(startIndex + basePath.length());
                    reviewMap.put("thumbnail", baseUrl + imagePath);
                } else {
                    reviewMap.put("thumbnail", baseUrl + fullPath); // basePath가 없을 경우 경로를 직접 사용
                }
            } else {
                reviewMap.put("thumbnail", ""); // 이미지가 없을 경우 빈 문자열로 처리
            }

            reviewList.add(reviewMap);
        }

        return ResponseEntity.ok(reviewList);
    }


}


