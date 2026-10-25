package com.campuslink.module.team.infrastructure.persistence;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campuslink.common.crypto.CryptoService;
import com.campuslink.module.team.domain.model.MyTeamItem;
import com.campuslink.module.team.domain.model.Team;
import com.campuslink.module.team.domain.model.TeamDirection;
import com.campuslink.module.team.domain.model.TeamListItem;
import com.campuslink.module.team.domain.model.TeamMember;
import com.campuslink.module.team.domain.model.TeamMemberProfile;
import com.campuslink.module.team.domain.model.TeamMemberRole;
import com.campuslink.module.team.domain.model.TeamMemberStatus;
import com.campuslink.module.team.domain.model.TeamStatus;
import com.campuslink.module.team.infrastructure.persistence.mapper.TeamMapper;
import com.campuslink.module.team.infrastructure.persistence.mapper.TeamMemberMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 组队适配器（CR-137）：联系方式加解密边界、投影映射、重复申请翻 false */
@ExtendWith(MockitoExtension.class)
class TeamRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Mock
    private TeamMapper teamMapper;
    @Mock
    private CryptoService cryptoService;
    @InjectMocks
    private TeamRepositoryImpl repository;

    @Mock
    private TeamMemberMapper teamMemberMapper;
    @InjectMocks
    private TeamMemberRepositoryImpl memberRepository;

    /** 纯单测没有 SqlSession，lambda 条件 / update 的列名解析依赖 MP 的 TableInfo 缓存，先手工预热 */
    @BeforeAll
    static void initTableInfoCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(TeamMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, TeamDO.class);
    }

    @Test
    @DisplayName("新建：联系方式加密后落库，返回的聚合仍持明文")
    void saveEncryptsContact() {
        when(cryptoService.encrypt("qq:123456")).thenReturn("ENC");
        when(teamMapper.insert(any(TeamDO.class))).thenAnswer(inv -> {
            TeamDO row = inv.getArgument(0);
            row.setId(10L);
            return 1;
        });
        Team team = team();

        Team saved = repository.save(team);

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getContact()).isEqualTo("qq:123456");
        verify(teamMapper).insert(any(TeamDO.class));
    }

    @Test
    @DisplayName("详情读取：密文解密后交回领域对象；列表投影映射完整")
    void findAndPageMapRows() {
        TeamDO row = new TeamDO();
        row.setId(10L);
        row.setOwnerId(1L);
        row.setTitle("蓝桥杯组队");
        row.setDescriptionMd("找队友");
        row.setDirection("ALGORITHM");
        row.setTarget("蓝桥杯");
        row.setCapacity(3);
        row.setContactEnc("ENC");
        row.setStatus("RECRUITING");
        row.setDeadline(LocalDate.of(2026, 11, 30));
        row.setCreatedAt(NOW);
        when(teamMapper.selectById(10L)).thenReturn(row);
        when(cryptoService.decrypt("ENC")).thenReturn("qq:123456");

        Optional<Team> found = repository.findById(10L);

        assertThat(found).isPresent();
        assertThat(found.get().getContact()).isEqualTo("qq:123456");
        assertThat(found.get().getDirection()).isEqualTo(TeamDirection.ALGORITHM);
        assertThat(found.get().getStatus()).isEqualTo(TeamStatus.RECRUITING);

        TeamListDO listRow = new TeamListDO();
        listRow.setId(10L);
        listRow.setTitle("蓝桥杯组队");
        listRow.setDirection("BACKEND");
        listRow.setTarget(null);
        listRow.setCapacity(4);
        listRow.setAcceptedCount(2L);
        listRow.setStatus("FULL");
        listRow.setDeadline(LocalDate.of(2026, 12, 1));
        listRow.setOwnerId(1L);
        listRow.setOwnerNickname("发起人");
        listRow.setCreatedAt(NOW);
        when(teamMapper.page(null, "RECRUITING", 0, 20)).thenReturn(List.of(listRow));

        List<TeamListItem> items = repository.page(null, TeamStatus.RECRUITING, 0, 20);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).acceptedCount()).isEqualTo(2L);
        assertThat(items.get(0).direction()).isEqualTo(TeamDirection.BACKEND);
        assertThat(items.get(0).status()).isEqualTo(TeamStatus.FULL);
        assertThat(items.get(0).ownerNickname()).isEqualTo("发起人");
    }

    @Test
    @DisplayName("我的组队投影：owner 带待审批数，member 带申请状态")
    void myTeamProjectionsMap() {
        MyTeamDO ownerRow = new MyTeamDO();
        ownerRow.setId(10L);
        ownerRow.setTitle("蓝桥杯组队");
        ownerRow.setDirection("ALGORITHM");
        ownerRow.setCapacity(3);
        ownerRow.setAcceptedCount(1L);
        ownerRow.setStatus("RECRUITING");
        ownerRow.setMyRole("OWNER");
        ownerRow.setMyStatus("ACCEPTED");
        ownerRow.setPendingCount(2L);
        ownerRow.setCreatedAt(NOW);
        when(teamMapper.pageOfOwner(1L, 0, 20)).thenReturn(List.of(ownerRow));

        MyTeamItem owner = repository.pageOfOwner(1L, 0, 20).get(0);
        assertThat(owner.myRole()).isEqualTo(TeamMemberRole.OWNER);
        assertThat(owner.pendingCount()).isEqualTo(2L);

        MyTeamDO memberRow = new MyTeamDO();
        memberRow.setId(11L);
        memberRow.setTitle("另一个队");
        memberRow.setDirection("DATA");
        memberRow.setCapacity(2);
        memberRow.setAcceptedCount(2L);
        memberRow.setStatus("FULL");
        memberRow.setMyRole("MEMBER");
        memberRow.setMyStatus("ACCEPTED");
        memberRow.setPendingCount(0L);
        memberRow.setCreatedAt(NOW);
        when(teamMapper.pageOfMember(2L, 0, 20)).thenReturn(List.of(memberRow));

        MyTeamItem member = repository.pageOfMember(2L, 0, 20).get(0);
        assertThat(member.myStatus()).isEqualTo(TeamMemberStatus.ACCEPTED);
        assertThat(member.pendingCount()).isZero();
    }

    @Test
    @DisplayName("审批锁行：selectOne 带 FOR UPDATE；状态迁移只按 status 更新")
    void findForUpdateAndUpdateStatus() {
        TeamDO row = new TeamDO();
        row.setId(10L);
        row.setOwnerId(1L);
        row.setTitle("蓝桥杯组队");
        row.setDescriptionMd("找队友");
        row.setDirection("ALGORITHM");
        row.setCapacity(3);
        row.setContactEnc("ENC");
        row.setStatus("RECRUITING");
        row.setCreatedAt(NOW);
        when(teamMapper.selectOne(any())).thenReturn(row);
        when(cryptoService.decrypt("ENC")).thenReturn("qq:123456");

        assertThat(repository.findByIdForUpdate(10L)).isPresent();

        repository.updateStatus(10L, TeamStatus.FULL);
        verify(teamMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("成员适配器：重复申请翻 false；关系映射与审批条件更新正确")
    void memberRepositoryHandlesDuplicateAndMaps() {
        when(teamMemberMapper.insert(any(TeamMemberDO.class)))
                .thenThrow(new DuplicateKeyException("uk"))
                .thenReturn(1);

        assertThat(memberRepository.tryAddApplication(10L, 2L, NOW)).isFalse();
        assertThat(memberRepository.tryAddApplication(10L, 2L, NOW)).isTrue();

        TeamMemberDO row = new TeamMemberDO();
        row.setTeamId(10L);
        row.setUserId(2L);
        row.setRole("MEMBER");
        row.setStatus("PENDING");
        row.setAppliedAt(NOW);
        when(teamMemberMapper.selectOne(10L, 2L)).thenReturn(row);
        TeamMember member = memberRepository.findByTeamAndUser(10L, 2L).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(TeamMemberStatus.PENDING);

        TeamMemberProfileDO profile = new TeamMemberProfileDO();
        profile.setUserId(1L);
        profile.setNickname("发起人");
        profile.setRole("OWNER");
        when(teamMemberMapper.acceptedProfiles(10L)).thenReturn(List.of(profile));
        List<TeamMemberProfile> profiles = memberRepository.acceptedProfiles(10L);
        assertThat(profiles.get(0).role()).isEqualTo(TeamMemberRole.OWNER);

        TeamApplicationDO application = new TeamApplicationDO();
        application.setUserId(2L);
        application.setNickname("申请人");
        application.setStatus("PENDING");
        application.setAppliedAt(NOW);
        when(teamMemberMapper.pendingApplications(10L, 0, 20)).thenReturn(List.of(application));
        assertThat(memberRepository.pendingApplications(10L, 0, 20).get(0).nickname()).isEqualTo("申请人");

        when(teamMemberMapper.updateStatus(10L, 2L, "ACCEPTED", NOW)).thenReturn(1);
        assertThat(memberRepository.updateStatus(10L, 2L, TeamMemberStatus.ACCEPTED, NOW)).isEqualTo(1);

        TeamMember owner = TeamMember.owner(10L, 1L, NOW);
        memberRepository.save(owner);
        verify(teamMemberMapper, times(3)).insert(any(TeamMemberDO.class));
    }

    private static Team team() {
        return Team.open(1L, "蓝桥杯组队", "找队友", TeamDirection.ALGORITHM, null, 3, "qq:123456", null, NOW);
    }
}
