package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.BoardResponse;
import com.workos.workos_backend.dto.CreateBoardRequest;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.dto.UpdateBoardRequest;
import com.workos.workos_backend.entity.BoardMeta;
import com.workos.workos_backend.service.BoardService;

import jakarta.validation.Valid;

/** Implements the Boards endpoint table from BACKEND.md. */
@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;
    private final ActingPersonResolver actingPersonResolver;

    public BoardController(BoardService boardService, ActingPersonResolver actingPersonResolver) {
        this.boardService = boardService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping
    public List<BoardResponse> list() {
        String actorId = actingPersonResolver.currentPersonId();
        return boardService.listVisibleBoards(actorId).stream().map(BoardResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardResponse create(@Valid @RequestBody CreateBoardRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        BoardMeta board = boardService.createBoard(
                actorId, request.workspaceId(), request.name(), request.description(), request.icon());
        return BoardResponse.from(board);
    }

    @PatchMapping("/{boardId}")
    public BoardResponse update(@PathVariable String boardId, @Valid @RequestBody UpdateBoardRequest request) {
        String actorId = actingPersonResolver.currentPersonId();
        BoardMeta board = boardService.updateBoard(
                actorId, boardId, request.name(), request.description(), request.icon());
        return BoardResponse.from(board);
    }

    @DeleteMapping("/{boardId}")
    public OkResponse delete(@PathVariable String boardId) {
        String actorId = actingPersonResolver.currentPersonId();
        boardService.deleteBoard(actorId, boardId);
        return OkResponse.OK;
    }
}
