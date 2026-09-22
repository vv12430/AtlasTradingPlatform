package com.atlas.common;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.*;
import org.springframework.stereotype.Component;

@Component
public class GraphErrors extends DataFetcherExceptionResolverAdapter {

  @Override
  protected GraphQLError resolveToSingleError(
    Throwable e,
    DataFetchingEnvironment env
  ) {
    if (
      e instanceof IllegalArgumentException ||
      e instanceof jakarta.validation.ConstraintViolationException
    ) return graphql.GraphqlErrorBuilder.newError(env)
      .errorType(ErrorType.BAD_REQUEST)
      .message(e.getMessage())
      .build();
    if (
      e instanceof IllegalStateException
    ) return graphql.GraphqlErrorBuilder.newError(env)
      .errorType(ErrorType.BAD_REQUEST)
      .message(e.getMessage())
      .build();
    return null;
  }
}
