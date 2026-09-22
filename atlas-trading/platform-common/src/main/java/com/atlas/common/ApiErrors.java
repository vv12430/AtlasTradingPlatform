package com.atlas.common;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ProblemDetail> bad(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(
      ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage())
    );
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ProblemDetail> conflict(IllegalStateException e) {
    return ResponseEntity.status(409).body(
      ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage())
    );
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  ResponseEntity<ProblemDetail> missing() {
    return ResponseEntity.status(404).body(
      ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        "Resource not found"
      )
    );
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity() {
    return ResponseEntity.status(409).body(
      ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT,
        "Duplicate or referenced resource"
      )
    );
  }
}
