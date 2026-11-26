package com.fishport.service;

import com.fishport.domain.CaseNote;
import com.fishport.domain.Types;
import com.fishport.domain.Voyage;
import com.fishport.domain.VoyageEvent;
import com.fishport.repo.CaseNoteRepository;
import com.fishport.repo.VoyageEventRepository;
import com.fishport.repo.VoyageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 案件备注：仅执法人员(ENFORCEMENT)的备注标记为执法事实；客服备注不能当成执法事实 */
@Service
public class NoteService {

    private final CaseNoteRepository noteRepo;
    private final VoyageRepository voyageRepo;
    private final VoyageEventRepository eventRepo;

    public NoteService(CaseNoteRepository noteRepo, VoyageRepository voyageRepo, VoyageEventRepository eventRepo) {
        this.noteRepo = noteRepo;
        this.voyageRepo = voyageRepo;
        this.eventRepo = eventRepo;
    }

    @Transactional
    public CaseNote addNote(String voyageNo, String content, String author, String role) {
        Voyage voyage = voyageRepo.findByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
        CaseNote note = new CaseNote();
        note.setVoyageId(voyage.getId());
        note.setAuthor(author);
        note.setAuthorRole(role);
        note.setContent(content);
        // 只有执法人员的备注才是执法事实；客服等其他角色一律不是
        note.setEnforcementFact("ENFORCEMENT".equals(role));
        noteRepo.save(note);

        VoyageEvent e = new VoyageEvent();
        e.setVoyageId(voyage.getId());
        e.setPermitId(voyage.getPermitId());
        e.setEventType(Types.VoyageEventType.NOTE_ADDED);
        e.setDetail((note.isEnforcementFact() ? "执法事实记录：" : "备注（非执法事实）：") + content);
        e.setActor(author);
        eventRepo.save(e);
        return note;
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<CaseNote> notesOf(String voyageNo) {
        Voyage voyage = voyageRepo.findByVoyageNo(voyageNo)
                .orElseThrow(() -> BizException.notFound("航次不存在: " + voyageNo));
        return noteRepo.findByVoyageIdOrderByCreatedAtAsc(voyage.getId());
    }
}
