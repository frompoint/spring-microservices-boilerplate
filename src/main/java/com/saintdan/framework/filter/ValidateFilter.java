package com.saintdan.framework.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saintdan.framework.annotation.NotNullField;
import com.saintdan.framework.annotation.SizeField;
import com.saintdan.framework.constant.CommonsConstant;
import com.saintdan.framework.enums.ErrorType;
import com.saintdan.framework.enums.ResourceUri;
import com.saintdan.framework.servlet.RequestWrapper;
import com.saintdan.framework.tools.LogUtils;
import com.saintdan.framework.vo.ErrorVO;
import java.io.IOException;
import java.lang.reflect.Field;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Validate helper.
 *
 * @author <a href="http://github.com/saintdan">Liao Yifan</a>
 * @date 11/4/15
 * @since JDK1.8
 */
@Component
@Order(1)
@WebFilter(filterName = "ValidateFilter")
public class ValidateFilter implements Filter {

  @Override public void init(FilterConfig filterConfig) throws ServletException {
    LogUtils.trackInfo(logger, "Initiating ValidateFilter");
  }

  @Override
  @SuppressWarnings("unchecked")
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest) {
      RequestWrapper req = new RequestWrapper((HttpServletRequest) request);
      StringBuilder stringBuilder = new StringBuilder();
      if (!(HttpMethod.GET.matches(req.getMethod())
          || HttpMethod.DELETE.matches(req.getMethod()))) {
        req.getReader().lines().forEach(stringBuilder::append);
        String json = stringBuilder.toString();
        ObjectMapper mapper = new ObjectMapper();
        String result = validate(
            mapper.readValue(json, ResourceUri.resolve(req.getRequestURI()).clazz()),
            HttpMethod.resolve(req.getMethod()));
        if (StringUtils.isNotBlank(result)) {
          HttpServletResponse resp = ((HttpServletResponse) response);
          resp.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
          resp.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
          resp.getWriter().write(mapper.writeValueAsString(
              ErrorVO.builder()
                  .error(ErrorType.SYS0002.name())
                  .error_description(result)
                  .build()));
          return;
        }
      }
      chain.doFilter(req, response);
    } else {
      ((HttpServletResponse) response).setStatus(HttpStatus.BAD_REQUEST.value());
    }
  }

  @Override public void destroy() {
    LogUtils.trackInfo(logger, "Destroying ValidateFilter");
  }

  private static final Logger logger = LoggerFactory.getLogger(ValidateFilter.class);

  private String validate(Object param, HttpMethod method) {
    Field[] fields = param.getClass().getDeclaredFields();
    for (Field field : fields) {
      if (field == null || !field.isAnnotationPresent(NotNullField.class)) {
        continue; // Ignore field without ParamField annotation.
      }
      field.setAccessible(true);
      NotNullField notNullField = field.getAnnotation(NotNullField.class);
      try {
        if (ArrayUtils.contains(notNullField.method(), method) && (field.get(param) == null
            || StringUtils
            .isBlank(field.get(param).toString()))) {
          return notNullField.message();
        }
      } catch (IllegalAccessException ignore) {
      }
      if (field.isAnnotationPresent(SizeField.class)) {
        SizeField size = field.getAnnotation(SizeField.class);
        try {
          if (ArrayUtils.contains(size.method(), method)
              && (field.get(param).toString().length() > size.max()
              || field.get(param).toString().length() < size.min())) {
            return notNullField.message();
          }
        } catch (IllegalAccessException ignore) {
        }
      }
    }
    return CommonsConstant.BLANK;
  }
}
