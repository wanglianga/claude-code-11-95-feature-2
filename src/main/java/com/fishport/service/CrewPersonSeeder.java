package com.fishport.service;

import com.fishport.domain.CrewPerson;
import com.fishport.repo.CrewPersonRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 人员主档种子（经 JPA 写入）。
 * 场景日 2026-11-26：
 * 原船长 P-CAP-01 09:00 还差两小时休息（11:00 才达标）；替补船长 P-CAP-02 09:15 休息达标；
 * P-ENG-01 是唯一持 REEFER 冷藏设备资质的轮机员，P-ENG-02 无该资质。
 * 必须走 JPA 而非 SQL 字面量：全局 hibernate.jdbc.time_zone=Asia/Shanghai 会平移裸 SQL 时间。
 */
@Component
public class CrewPersonSeeder implements ApplicationRunner {

    private final CrewPersonRepository personRepo;

    public CrewPersonSeeder(CrewPersonRepository personRepo) {
        this.personRepo = personRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (personRepo.count() > 0) {
            return;
        }
        LocalDateTime certValid = LocalDateTime.of(2027, 12, 31, 0, 0);
        LocalDateTime restedEarly = LocalDateTime.of(2026, 11, 26, 6, 0);
        personRepo.save(person("P-CAP-01", "船长张", "CAPTAIN", "CERT-CAP-01",
                "TRAWLER,LONGLINER", null, LocalDateTime.of(2026, 11, 26, 11, 0), "13900000001", certValid));
        personRepo.save(person("P-CAP-02", "替补船长李", "CAPTAIN", "CERT-CAP-02",
                "TRAWLER,LONGLINER", null, LocalDateTime.of(2026, 11, 26, 9, 15), "13900000002", certValid));
        personRepo.save(person("P-ENG-01", "轮机员王", "ENGINEER", "CERT-ENG-01",
                "TRAWLER", "REEFER", restedEarly, "13900000003", certValid));
        personRepo.save(person("P-ENG-02", "轮机员赵", "ENGINEER", "CERT-ENG-02",
                "TRAWLER", null, restedEarly, "13900000004", certValid));
        personRepo.save(person("P-SAIL-01", "船员陈", "SAILOR", "CERT-SAIL-01",
                "TRAWLER", null, restedEarly, "13900000005", certValid));
    }

    private CrewPerson person(String no, String name, String position, String certNo, String grants,
                              String quals, LocalDateTime restReady, String phone, LocalDateTime certValid) {
        CrewPerson p = new CrewPerson();
        p.setPersonNo(no);
        p.setName(name);
        p.setPositionCode(position);
        p.setCertificateNo(certNo);
        p.setCertValidTo(certValid);
        p.setVesselTypeGrants(grants);
        p.setQualificationCodes(quals);
        p.setRestReadyAt(restReady);
        p.setEmergencyPhone(phone);
        p.setActive(true);
        return p;
    }
}
