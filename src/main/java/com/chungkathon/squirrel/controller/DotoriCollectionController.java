package com.chungkathon.squirrel.controller;

import com.chungkathon.squirrel.domain.DotoriCollection;
import com.chungkathon.squirrel.domain.Member;
import com.chungkathon.squirrel.domain.Quiz;
import com.chungkathon.squirrel.dto.request.DotoriCollectionCreateRequestDto;
import com.chungkathon.squirrel.dto.request.QuizReplyCreateRequestDto;
import com.chungkathon.squirrel.dto.request.RedundancyCheckRequest;
import com.chungkathon.squirrel.dto.response.DotoriCollectionCreateDto;
import com.chungkathon.squirrel.dto.response.DotoriCollectionResponseDto;
import com.chungkathon.squirrel.dto.response.QuizResponseDto;
import com.chungkathon.squirrel.repository.DotoriCollectionJpaRepository;
import com.chungkathon.squirrel.repository.MemberJpaRepository;
import com.chungkathon.squirrel.service.DotoriCollectionService;
import com.chungkathon.squirrel.service.DotoriService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/dotoricollection")
public class DotoriCollectionController {
    private final DotoriCollectionJpaRepository dotoriCollectionJpaRepository;
    private DotoriCollectionService dotoriCollectionService;
    private DotoriService dotoriService;
    private MemberJpaRepository memberJpaRepository;

    public DotoriCollectionController(DotoriCollectionService dotoriCollectionService,
                                      DotoriCollectionJpaRepository dotoriCollectionJpaRepository,
                                      DotoriService dotoriService,
                                      MemberJpaRepository memberJpaRepository) {
        this.dotoriCollectionService = dotoriCollectionService;
        this.dotoriCollectionJpaRepository = dotoriCollectionJpaRepository;
        this.dotoriService = dotoriService;
        this.memberJpaRepository = memberJpaRepository;
    }

    // 사용자별 도토리 주머니 모아보기 (삭제된 도토리 주머니 제외)
    @GetMapping("/{urlRnd}")
    public ResponseEntity<List<DotoriCollectionResponseDto>> getDotoriCollection(@PathVariable String urlRnd) {
        List<DotoriCollection> dotoriCollections = dotoriCollectionService.getActiveDotoriCollections(urlRnd);

        List<DotoriCollectionResponseDto> responseDtos = dotoriCollections.stream()
                .map(collection -> new DotoriCollectionResponseDto(
                        collection.getId(),
                        collection.getSender(),
                        collection.getMessage(),
                        collection.isLock(),
                        collection.isDeleted(),
                        collection.getDotoriNum(),
                        collection.getCreatedAt(),
                        collection.getUpdatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responseDtos);
    }

    // 도토리 주머니 생성
    @PostMapping("/{urlRnd}/create")
    public DotoriCollectionCreateDto createDotoriCollection(@PathVariable String urlRnd,
                                                            @RequestParam("requestJson") String requestDtoString,
                                                            @RequestParam("files")List<MultipartFile> files) {
        int fileCount = files.size();

        // 요청 문자열을 JSON으로
        ObjectMapper objectMapper = new ObjectMapper();
        DotoriCollectionCreateRequestDto requestDto;
        try {
            requestDto = objectMapper.readValue(requestDtoString, DotoriCollectionCreateRequestDto.class);
            System.out.println(requestDto);
        } catch (JsonProcessingException e) {
            throw  new RuntimeException("Invalid JSON String");
        }

        DotoriCollectionCreateDto responseDto = dotoriCollectionService.createDotoriCollection(urlRnd, requestDto);
        DotoriCollection dotoriCollection = dotoriCollectionJpaRepository.findById(responseDto.getId())
                .orElseThrow(() -> new IllegalArgumentException("해당 ID에 해당하는 도토리 가방이 존재하지 않습니다."));
        dotoriService.createMultipleDotori(files, dotoriCollection);

        responseDto.setDotoriNum(fileCount);
        return responseDto;
    }

    @GetMapping("/{dotori_collection_id}/quiz")
    public ResponseEntity<?> getQuizByDotoriCollectionId(@PathVariable Long dotori_collection_id) {
        boolean isOwner = dotoriCollectionService.isDotoriCollectionOwner(dotori_collection_id);

        if (!isOwner) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "fail");
            response.put("message", "사용자가 도토리 주머니의 소유자가 아닙니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        DotoriCollection dotoriCollection = dotoriCollectionService.getDotoriCollection(dotori_collection_id);
        Quiz quiz = dotoriCollection.getQuiz();

        if (quiz == null) {
            return ResponseEntity.status(404).body("연결된 퀴즈를 찾을 수 없습니다.");
        }
        QuizResponseDto responseDto = new QuizResponseDto(
                quiz.getId(),
                quiz.getQuestion(),
                quiz.getAnswer(),
                dotoriCollection.getSender()
        );

        return ResponseEntity.ok().body(responseDto);
    }

    // 잠금 해제 전 퀴즈 응답
    @PutMapping("/{dotori_collection_id}/reply")
    public ResponseEntity<Map<String, String>> checkDotoriCollection(@PathVariable Long dotori_collection_id, @RequestBody QuizReplyCreateRequestDto requestDto) {
        boolean isOwner = dotoriCollectionService.isDotoriCollectionOwner(dotori_collection_id);
        boolean isCorrect = dotoriCollectionService.updateDotoriCollection(isOwner, dotori_collection_id, requestDto);

        // 도토리 가방의 주인일 경우
        if (isOwner) {
            if (isCorrect) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "정답입니다. 도토리 주머니의 잠금이 해제되었습니다.");

                return ResponseEntity.ok(response);
            }
            else {
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "틀렸습니다. 도토리 주머니가 삭제되었습니다.");

                return ResponseEntity.ok(response);
            }
        }

        // 도토리 가방의 주인이 아닐 경우
        else {
            Map<String, String> response = new HashMap<>();
            response.put("status", "fail");
            response.put("message", "사용자가 도토리의 주인이 아닙니다.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
    }

    // 도토리 주머니 열어보기
    @GetMapping("/{dotori_collection_id}/open")
    public ResponseEntity<?> getDotoriCollection(@PathVariable Long dotori_collection_id) {
        DotoriCollection dotoriCollection = dotoriCollectionService.getDotoriCollection(dotori_collection_id);

        if (dotoriCollection == null) {
            return ResponseEntity.status(404).body("도토리 주머니를 찾을 수 없습니다.");
        }

        if (dotoriCollection.isDeleted()){
            return ResponseEntity.status(403).body("삭제된 도토리 주머니에 접근할 수 없습니다.");
        }

        if (dotoriCollection.isLock()) {
            return ResponseEntity.status(403).body("도토리 주머니가 잠겨 있습니다.");
        }

        DotoriCollectionResponseDto responseDto = new DotoriCollectionResponseDto(
                dotoriCollection.getId(),
                dotoriCollection.getSender(),
                (String) dotoriCollection.getMessage(),
                dotoriCollection.getDotoriNum(),
                dotoriCollection.getCreatedAt(),
                dotoriCollection.getUpdatedAt()
        );

        return ResponseEntity.ok().body(responseDto);
    }

    @GetMapping("/{urlRnd}/isFull")
    public ResponseEntity<Map<String, Boolean>> getIsFull(@PathVariable String urlRnd) {
        boolean isFull = dotoriCollectionService.isDotoriCollectionFull(urlRnd);

        Map<String, Boolean> response = new HashMap<>();
        response.put("isFull", isFull);

        return ResponseEntity.ok(response);
    }
}
