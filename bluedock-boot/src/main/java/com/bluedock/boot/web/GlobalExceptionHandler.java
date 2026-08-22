package com.bluedock.boot.web;

import com.bluedock.common.exception.BusinessException;
import com.bluedock.common.exception.ErrorCodes;
import com.bluedock.common.i18n.I18nKeys;
import com.bluedock.common.i18n.Messages;
import com.bluedock.common.model.ResultModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BusinessException.class)
  public ResultModel<Void> handleBusiness(BusinessException ex) {
    return ResultModel.fail(ex.getCode(), ex.resolvedMessage());
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class
  })
  public ResultModel<Void> handleBadRequest(Exception ex) {
    return ResultModel.fail(ErrorCodes.BAD_REQUEST, Messages.get(I18nKeys.BAD_REQUEST));
  }

  @ExceptionHandler(Exception.class)
  public ResultModel<Void> handleOther(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResultModel.fail(ErrorCodes.BAD_REQUEST, Messages.get(I18nKeys.ERROR));
  }
}
