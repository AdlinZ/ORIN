package com.adlin.orin.modules.conversation.tooling;

import com.adlin.orin.modules.conversation.dto.ChatMessageRequest;
import com.adlin.orin.modules.conversation.dto.tooling.EffectiveToolBinding;
import com.adlin.orin.modules.conversation.dto.tooling.ToolBindingDto;
import com.adlin.orin.modules.conversation.entity.AgentChatSession;
import com.adlin.orin.modules.conversation.entity.AgentToolBinding;
import com.adlin.orin.modules.conversation.entity.SessionToolBinding;
import com.adlin.orin.modules.conversation.repository.AgentToolBindingRepository;
import com.adlin.orin.modules.conversation.repository.SessionToolBindingRepository;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolBindingService {

    private final AgentToolBindingRepository agentToolBindingRepository;
    private final SessionToolBindingRepository sessionToolBindingRepository;
    private final SkillRepository skillRepository;
    private final McpServiceRepository mcpServiceRepository;

    @Value("${orin.conversation.default-bind-active-skills:false}")
    private boolean defaultBindActiveSkills;
    @Value("${orin.conversation.default-bind-connected-mcp:false}")
    private boolean defaultBindConnectedMcp;

    public ToolBindingDto getAgentBinding(String agentId) {
        AgentToolBinding binding = agentToolBindingRepository.findById(agentId)
                .orElseGet(() -> AgentToolBinding.builder().agentId(agentId).build());
        return toDto(binding);
    }

    public ToolBindingDto saveAgentBinding(String agentId, ToolBindingDto request) {
        AgentToolBinding binding = agentToolBindingRepository.findById(agentId)
                .orElseGet(() -> AgentToolBinding.builder().agentId(agentId).build());

        binding.setToolIds(normalizeStringList(request.getToolIds()));
        binding.setKbIds(normalizeStringList(request.getKbIds()));
        binding.setSkillIds(normalizeLongList(request.getSkillIds()));
        binding.setMcpIds(normalizeLongList(request.getMcpIds()));

        if (request.getEnableSuggestions() != null) binding.setEnableSuggestions(request.getEnableSuggestions());
        if (request.getShowRetrievedContext() != null) binding.setShowRetrievedContext(request.getShowRetrievedContext());
        if (request.getAutoRenameSession() != null) binding.setAutoRenameSession(request.getAutoRenameSession());

        return toDto(agentToolBindingRepository.save(binding));
    }

    public ToolBindingDto getSessionBinding(String sessionId, String agentId) {
        SessionToolBinding binding = sessionToolBindingRepository.findById(sessionId)
                .orElseGet(() -> SessionToolBinding.builder().sessionId(sessionId).agentId(agentId).build());
        return toDto(binding);
    }

    public ToolBindingDto saveSessionBinding(String sessionId, String agentId, ToolBindingDto request) {
        SessionToolBinding binding = sessionToolBindingRepository.findById(sessionId)
                .orElseGet(() -> SessionToolBinding.builder().sessionId(sessionId).agentId(agentId).build());

        binding.setAgentId(agentId);
        binding.setToolIds(normalizeStringList(request.getToolIds()));
        binding.setKbIds(normalizeStringList(request.getKbIds()));
        binding.setSkillIds(normalizeLongList(request.getSkillIds()));
        binding.setMcpIds(normalizeLongList(request.getMcpIds()));

        return toDto(sessionToolBindingRepository.save(binding));
    }

    public EffectiveToolBinding resolveEffectiveBinding(AgentChatSession session, ChatMessageRequest request) {
        AgentToolBinding agentBinding = session != null && session.getAgentId() != null
                ? agentToolBindingRepository.findById(session.getAgentId()).orElse(null)
                : null;
        SessionToolBinding sessionBinding = session != null
                ? sessionToolBindingRepository.findById(session.getSessionId()).orElse(null)
                : null;

        List<String> effectiveKbIds = firstNonEmpty(
                request != null ? request.getKbIds() : null,
                sessionBinding != null ? sessionBinding.getKbIds() : null,
                agentBinding != null ? agentBinding.getKbIds() : null,
                session != null ? session.getAttachedKbIds() : null);

        List<Long> effectiveSkillIds = firstNonEmptyLong(
                request != null ? request.getSkillIds() : null,
                sessionBinding != null ? sessionBinding.getSkillIds() : null,
                agentBinding != null ? agentBinding.getSkillIds() : null,
                List.of());

        boolean explicitToolSelection = request != null
                && ((request.getToolIds() != null && !request.getToolIds().isEmpty())
                || (request.getSkillIds() != null && !request.getSkillIds().isEmpty())
                || (request.getMcpIds() != null && !request.getMcpIds().isEmpty()));
        if (defaultBindActiveSkills && !explicitToolSelection && effectiveSkillIds.isEmpty()) {
            effectiveSkillIds = skillRepository.findByStatus(SkillEntity.SkillStatus.ACTIVE).stream()
                    .map(SkillEntity::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
        }

        List<Long> effectiveMcpIds = firstNonEmptyLong(
                request != null ? request.getMcpIds() : null,
                sessionBinding != null ? sessionBinding.getMcpIds() : null,
                agentBinding != null ? agentBinding.getMcpIds() : null,
                List.of());
        if (defaultBindConnectedMcp && !explicitToolSelection && effectiveMcpIds.isEmpty()) {
            effectiveMcpIds = mcpServiceRepository.findByStatus(McpService.McpStatus.CONNECTED).stream()
                    .filter(service -> Boolean.TRUE.equals(service.getEnabled()))
                    .map(McpService::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
        }

        List<String> effectiveToolIds = firstNonEmpty(
                request != null ? request.getToolIds() : null,
                sessionBinding != null ? sessionBinding.getToolIds() : null,
                agentBinding != null ? agentBinding.getToolIds() : null,
                deriveToolIds(effectiveKbIds, effectiveSkillIds, effectiveMcpIds),
                List.of());

        log.info("Effective tool binding resolved: agentId={}, sessionId={}, toolIds={}, kbIds={}, skillIds={}, mcpIds={}",
                session != null ? session.getAgentId() : null,
                session != null ? session.getSessionId() : null,
                effectiveToolIds,
                effectiveKbIds,
                effectiveSkillIds,
                effectiveMcpIds);

        return EffectiveToolBinding.builder()
                .agentId(session != null ? session.getAgentId() : null)
                .sessionId(session != null ? session.getSessionId() : null)
                .toolIds(effectiveToolIds)
                .kbIds(effectiveKbIds)
                .skillIds(effectiveSkillIds)
                .mcpIds(effectiveMcpIds)
                .enableSuggestions(agentBinding != null ? agentBinding.getEnableSuggestions() : true)
                .showRetrievedContext(agentBinding != null ? agentBinding.getShowRetrievedContext() : true)
                .autoRenameSession(agentBinding != null ? agentBinding.getAutoRenameSession() : true)
                .build();
    }

    private List<String> deriveToolIds(List<String> kbIds, List<Long> skillIds, List<Long> mcpIds) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (kbIds != null && !kbIds.isEmpty()) {
            // All KB built-in tools — always available when KB is attached
            merged.add("query_kb");
            merged.add("read_document");
            merged.add("kb_structure_scan");
            merged.add("list_knowledge_bases");
            merged.add("list_documents");
            merged.add("get_document_metadata");
            merged.add("get_kb_info");
            merged.add("list_knowledge_graphs");
            merged.add("get_graph_info");
            merged.add("list_graph_entities");
            merged.add("search_graph_entities");
        }
        if (skillIds != null) {
            skillIds.stream().filter(id -> id != null).forEach(id -> merged.add("skill:" + id));
        }
        if (mcpIds != null) {
            mcpIds.stream().filter(id -> id != null).forEach(id -> merged.add("mcp:" + id));
        }
        return new ArrayList<>(merged);
    }

    private ToolBindingDto toDto(AgentToolBinding binding) {
        return ToolBindingDto.builder()
                .scope("agent")
                .agentId(binding.getAgentId())
                .toolIds(normalizeStringList(binding.getToolIds()))
                .kbIds(normalizeStringList(binding.getKbIds()))
                .skillIds(normalizeLongList(binding.getSkillIds()))
                .mcpIds(normalizeLongList(binding.getMcpIds()))
                .enableSuggestions(binding.getEnableSuggestions())
                .showRetrievedContext(binding.getShowRetrievedContext())
                .autoRenameSession(binding.getAutoRenameSession())
                .build();
    }

    private ToolBindingDto toDto(SessionToolBinding binding) {
        return ToolBindingDto.builder()
                .scope("session")
                .agentId(binding.getAgentId())
                .sessionId(binding.getSessionId())
                .toolIds(normalizeStringList(binding.getToolIds()))
                .kbIds(normalizeStringList(binding.getKbIds()))
                .skillIds(normalizeLongList(binding.getSkillIds()))
                .mcpIds(normalizeLongList(binding.getMcpIds()))
                .build();
    }

    @SafeVarargs
    private final List<String> firstNonEmpty(List<String>... candidates) {
        for (List<String> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return normalizeStringList(candidate);
            }
        }
        return new ArrayList<>();
    }

    @SafeVarargs
    private final List<Long> firstNonEmptyLong(List<Long>... candidates) {
        for (List<Long> candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return normalizeLongList(candidate);
            }
        }
        return new ArrayList<>();
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) return new ArrayList<>();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return new ArrayList<>(set);
    }

    private List<Long> normalizeLongList(List<Long> values) {
        if (values == null) return new ArrayList<>();
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null) set.add(value);
        }
        return new ArrayList<>(set);
    }
}
