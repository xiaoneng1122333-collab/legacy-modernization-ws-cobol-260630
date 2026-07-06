package com.practicebank.online.inquiry;

import com.practicebank.masters.account.AccountServiceApplication;
import com.practicebank.masters.customer.CustomerServiceApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

/**
 * 照会オンライン API (18-inquiry / INQ-MAIN)。
 *
 * <p>COBOL の INQ-MAIN を Spring Boot REST に移植したもので、既存
 * customer-service / account-service の MyBatis マッパーを直接再利用する
 * (新規の SQL は書かず、既存の SELECT を API 経由で公開する Phase 2)。
 *
 * <p>customer-service / account-service は {@code @SpringBootApplication}
 * 付きのフルアプリとしても配布されるため、{@link ComponentScan} から
 * 除外してマッパー・リポジトリのみを組み込む。
 */
@SpringBootApplication(exclude = {MybatisAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.practicebank.common",
        "com.practicebank.online.inquiry",
        "com.practicebank.masters.customer",
        "com.practicebank.masters.account"
}, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {CustomerServiceApplication.class, AccountServiceApplication.class}
))
@MapperScan(basePackages = {
        "com.practicebank.masters.customer",
        "com.practicebank.masters.account"
})
public class InquiryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(InquiryApiApplication.class, args);
    }
}
