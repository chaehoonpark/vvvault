package org.hiring.api.repository.company.query;

import org.assertj.core.groups.Tuple;
import org.hiring.api.common.AbstractJpaTest;
import org.hiring.api.entity.CompanyJpaEntity;
import org.hiring.api.repository.company.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Import(CompanyQueryRepositoryImpl.class)
@DisplayName("CompanyQueryRepository 테스트")
class CompanyQueryRepositoryImplTest extends AbstractJpaTest {

    @Autowired
    private CompanyQueryRepository repository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    @DisplayName("산업별 필터링이 정확히 동작한다")
    void filter_by_industry() {
        // given - 구분되는 데이터들
        List<CompanyJpaEntity> companies = List.of(
                createCompany("IT회사1", "IT"),
                createCompany("IT회사2", "IT"),
                createCompany("금융회사", "금융"),
                createCompany("제조회사", "제조")
        );

        final var savedCompanies = companyRepository.saveAll(companies);
        flushAndClear();

        CompanySearchCondition condition = fixtureMonkey.giveMeBuilder(CompanySearchCondition.class)
                .set("industry", "IT")
                .setNull("address")
                .setNull("keywords")
                .set("offset", 0)
                .set("limit", 10)
                .sample();

        // when
        final var actualResult = repository.loadCompaniesPage(condition);

        // then - 결과로만 검증
        assertThat(actualResult)
                .isNotEmpty()
                .hasSize(2)
                .extracting(
                        CompanyJpaEntity::getId,
                        CompanyJpaEntity::getIndustry
                )
                .containsExactlyInAnyOrder(
                        savedCompanies.subList(0, 2)
                                .stream()
                                .map(
                                        company -> tuple(
                                                company.getId(),
                                                company.getIndustry()
                                        )
                                ).toArray(Tuple[]::new)
                );
    }

    @Test
    @DisplayName("키워드 검색이 name과 description 모두에서 동작한다")
    void searchKeywords() {
        // given
        List<CompanyJpaEntity> companies = List.of(
                createCompanyWithDescription("테크솔루션", "IT", "일반 설명"),           // name 매칭
                createCompanyWithDescription("일반회사", "금융", "AI 기술 전문회사"),      // description 매칭
                createCompanyWithDescription("다른회사", "제조", "일반적인 제조업체")      // 매칭 안됨
        );
        companyRepository.saveAll(companies);
        flushAndClear();

        CompanySearchCondition condition = fixtureMonkey.giveMeBuilder(CompanySearchCondition.class)
                .set("keywords", List.of("테크", "AI"))
                .setNull("address").setNull("industry")
                .set("offset", 0).set("limit", 10)
                .sample();

        // when
        final var result = repository.loadCompaniesPage(condition);

        // then - OR 검색이 제대로 되었는지 결과로 확인
        assertThat(result).hasSize(2)
                .extracting("name")
                .containsExactlyInAnyOrder("테크솔루션", "일반회사");
    }

    @Test
    @DisplayName("복합 조건 검색시 AND 조건으로 동작한다")
    void complexSearchWithAndCondition() {
        // given
        List<CompanyJpaEntity> companies = List.of(
                createCompanyWithAddress("서울IT", "IT", "서울시 강남구"),         // 모든 조건 만족
                createCompanyWithAddress("서울금융", "금융", "서울시 중구"),        // 주소만 만족
                createCompanyWithAddress("부산IT", "IT", "부산시"),              // 산업만 만족
                createCompanyWithAddress("경기IT", "IT", "경기도 성남시")          // 산업만 만족
        );
        companyRepository.saveAll(companies);
        flushAndClear();

        CompanySearchCondition condition = fixtureMonkey.giveMeBuilder(CompanySearchCondition.class)
                .set("address", "서울")
                .set("industry", "IT")
                .setNull("keywords")
                .set("offset", 0).set("limit", 10)
                .sample();

        // when
        final var result = repository.loadCompaniesPage(condition);

        // then - AND 조건이 제대로 적용되었는지 확인
        assertThat(result).hasSize(1)
                .extracting("name")
                .containsExactly("서울IT");
    }

    @Test
    @DisplayName("정렬이 createdAt 내림차순으로 동작한다")
    void orderByCreatedAtDesc() {
        // given - 시간 차이를 두고 저장
        CompanyJpaEntity oldCompany = createCompany("옛날회사", "IT");
        companyRepository.save(oldCompany);

        CompanyJpaEntity newCompany = createCompany("새회사", "IT");
        companyRepository.save(newCompany);
        flushAndClear();

        CompanySearchCondition condition = fixtureMonkey.giveMeBuilder(CompanySearchCondition.class)
                .setNull("address").setNull("industry").setNull("keywords")
                .set("offset", 0).set("limit", 10)
                .sample();

        // when
        final var result = repository.loadCompaniesPage(condition);

        // then - 최신 순으로 정렬되었는지 확인
        assertThat(result).hasSize(2)
                .extracting("name")
                .containsExactly("새회사", "옛날회사"); // 최신순
    }

    @Test
    @DisplayName("count와 load 결과가 일치한다")
    void countAndLoadConsistency() {
        // given
        List<CompanyJpaEntity> companies = List.of(
                createCompany("IT회사1", "IT"),
                createCompany("IT회사2", "IT"),
                createCompany("금융회사", "금융")
        );
        companyRepository.saveAll(companies);
        flushAndClear();

        CompanySearchCondition condition = fixtureMonkey.giveMeBuilder(CompanySearchCondition.class)
                .set("industry", "IT")
                .setNull("address").setNull("keywords")
                .set("offset", 0).set("limit", 10)
                .sample();

        // when
        final var loadResult = repository.loadCompaniesPage(condition);
        long countResult = repository.countCompanies(condition);

        // then - 같은 조건으로 count와 load 결과가 일치하는지
        assertThat(loadResult).hasSize((int) countResult);
        assertThat(countResult).isEqualTo(2);
    }

    private CompanyJpaEntity createCompany(final String name, final String industry) {
        return fixtureMonkey.giveMeBuilder(CompanyJpaEntity.class)
                .set("name", name)
                .set("industry", industry)
                .set("jobs", null)
                .sample();
    }

    private CompanyJpaEntity createCompanyWithAddress(final String name, final String industry, final String address) {
        return fixtureMonkey.giveMeBuilder(CompanyJpaEntity.class)
                 .set("name", name)
                 .set("industry", industry)
                 .set("address", address)
                 .set("jobs", null)
                 .sample();
    }

    private CompanyJpaEntity createCompanyWithDescription(final String name, final String industry, final String description) {
        return fixtureMonkey.giveMeBuilder(CompanyJpaEntity.class)
                 .set("name", name)
                 .set("industry", industry)
                 .set("description", description)
                 .set("jobs", null)
                 .sample();
    }
}
