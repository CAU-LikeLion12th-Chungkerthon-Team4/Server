package com.chungkathon.squirrel.service;

import com.chungkathon.squirrel.domain.DotoriCollection;
import com.chungkathon.squirrel.domain.Member;
import com.chungkathon.squirrel.domain.Quiz;
import com.chungkathon.squirrel.domain.QuizReply;
import com.chungkathon.squirrel.dto.request.DotoriCollectionCreateRequestDto;
import com.chungkathon.squirrel.dto.request.QuizCreateRequestDto;
import com.chungkathon.squirrel.dto.request.QuizReplyCreateRequestDto;
import com.chungkathon.squirrel.dto.response.DotoriCollectionCreateDto;
import com.chungkathon.squirrel.dto.response.DotoriCollectionResponseDto;
import com.chungkathon.squirrel.dto.response.MemberResponse;
import com.chungkathon.squirrel.repository.DotoriCollectionJpaRepository;
import com.chungkathon.squirrel.repository.MemberJpaRepository;
import com.chungkathon.squirrel.repository.QuizJpaRepository;
import com.chungkathon.squirrel.repository.QuizReplyJpaRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Getter
public class DotoriCollectionService {
    @Autowired
    private DotoriCollectionJpaRepository dotoriCollectionJpaRepository;
    @Autowired
    private QuizService quizService;
    @Autowired
    private QuizJpaRepository quizJpaRepository;
    @Autowired
    private QuizReplyJpaRepository quizReplyJpaRepository;
    @Autowired
    private MemberJpaRepository memberRepository;

    public DotoriCollectionService(DotoriCollectionJpaRepository dotoriCollectionJpaRepository) {
        this.dotoriCollectionJpaRepository = dotoriCollectionJpaRepository;
//        this.quizService = quizService;
    }

    @Transactional
    public DotoriCollectionCreateDto createDotoriCollection(String urlRnd, DotoriCollectionCreateRequestDto requestDto) {
        QuizCreateRequestDto quizRequest = new QuizCreateRequestDto();
        quizRequest.setQuestion(requestDto.getQuestion());
        quizRequest.setAnswer(requestDto.getAnswer());
        Quiz quiz = quizService.createQuiz(quizRequest);
        Member member = memberRepository.findByUrlRnd(urlRnd)
                .orElseThrow(() -> new IllegalArgumentException("Member with urlRnd " + urlRnd + " not found"));

        DotoriCollection dotoriCollection = DotoriCollection.builder()
                .sender(requestDto.getSender())
                .message(requestDto.getMessage())
                .lock(true)
                .deleted(false)
                .dotori_num(0)
                .quiz(quiz)
                .build();

        member.addDotoriCollection(dotoriCollection);
        quiz.setDotoriCollection(dotoriCollection);
        dotoriCollectionJpaRepository.save(dotoriCollection);
        quizJpaRepository.save(quiz);

        DotoriCollectionCreateDto dotoriCollectionCreateDto = DotoriCollectionCreateDto.builder()
                .id(dotoriCollection.getId())
                .sender(dotoriCollection.getSender())
                .message(dotoriCollection.getMessage())
                .lock(dotoriCollection.isLock())
                .deleted(dotoriCollection.isDeleted())
                .dotoriNum(dotoriCollection.getDotori_num())
                .createdAt(dotoriCollection.getCreatedAt()) // 날짜 값 매핑
                .updatedAt(dotoriCollection.getUpdatedAt())
                .member(MemberResponse.builder()
                        .id(member.getId())
                        .username(member.getUsername())
                        .nickname(member.getNickname())
                        .squirrelType(member.getSquirrelType())
                        .urlRnd(member.getUrlRnd())
                        .build())
                .build();

        return dotoriCollectionCreateDto;
    }

    @Transactional
    public boolean updateDotoriCollection(Long dotori_collection_id, QuizReplyCreateRequestDto requestDto) {
        DotoriCollection dotoriCollection = dotoriCollectionJpaRepository.findById(dotori_collection_id)
                .orElseThrow(() -> new RuntimeException("해당 ID를 가진 도토리 주머니가 없습니다."));

        Quiz quiz = dotoriCollection.getQuiz();
        if (quiz == null) {
            throw new RuntimeException("도토리 주머니에 연결된 퀴즈가 없습니다.");
        }

        QuizReply quizReply = new QuizReply(quiz, requestDto.getReply());
//        quizReply.setQuiz(quiz);
//        quizReply.setReply(requestDto.getReply());
        quizReplyJpaRepository.save(quizReply);

        boolean isCorrect = quizService.checkQuizReply(dotoriCollection.getId() , requestDto);

        if (isCorrect) { // 퀴즈 정답과 응답이 일치하면
            dotoriCollection.setLock(false); // 도토리 주머니 잠금 해제
        }
        else { // 퀴즈 정답과 응답이 불일치하면
            dotoriCollection.setDeleted(true); // 도토리 주머니 삭제
        }
        return isCorrect;
    }

    @Transactional
    public DotoriCollection getDotoriCollection(Long dotori_collection_id){
        DotoriCollection dotoriCollection = dotoriCollectionJpaRepository.findById(dotori_collection_id)
                .orElseThrow(() -> new RuntimeException("해당 ID를 가진 도토리 주머니가 없습니다."));

        return dotoriCollection;
    }

    @Transactional
    public void deleteDotoriCollection(Long dotori_collection_id) {
        DotoriCollection dotoriCollection = dotoriCollectionJpaRepository.findById(dotori_collection_id)
                .orElseThrow(() -> new RuntimeException("해당 ID를 가진 도토리 주머니가 없습니다."));
//        DotoriCollection dotoriCollection = getDotoriCollection(dotori_collection_id);
        dotoriCollectionJpaRepository.delete(dotoriCollection);
    }

    @Transactional
    public List<DotoriCollection> getActiveDotoriCollections(String urlRnd) {
        return dotoriCollectionJpaRepository.findActiveDotoriCollectionsByMember(urlRnd);
    }
}
