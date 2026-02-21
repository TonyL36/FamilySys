// MemberService.java
package service;

import model.Member;
import repository.MemberRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.List;

public class MemberService {
    private static final Logger logger = LogManager.getLogger(MemberService.class);
    private MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public Member addMember(String name, int generation, int gender) {
        try {
            Member member = memberRepository.addMember(name, generation, gender);
            logger.info("Member added: {}", member);
            return member;
        } catch (SQLException e) {
            logger.error("Error adding member: {}", e.getMessage());
            throw new RuntimeException("Error adding member", e);
        }
    }

    public Member addMember(String name, int generation, int gender, String remark) {
        try {
            Member member = memberRepository.addMember(name, generation, gender, remark);
            logger.info("Member added: {}", member);
            return member;
        } catch (SQLException e) {
            logger.error("Error adding member: {}", e.getMessage());
            throw new RuntimeException("Error adding member", e);
        }
    }

    public Member findMemberById(int memberId) {
        try {
            Member member = memberRepository.findMemberById(memberId);
            if (member == null) {
                logger.warn("Member not found with ID: {}", memberId);
            } else {
                logger.info("Member found: {}", member);
            }
            return member;
        } catch (SQLException e) {
            logger.error("Error finding member by ID: {}", e.getMessage());
            throw new RuntimeException("Error finding member", e);
        }
    }

    public Member findMemberByName(String name) {
        try {
            Member member = memberRepository.findMemberByName(name);
            if (member == null) {
                logger.warn("Member not found with name: {}", name);
            } else {
                logger.info("Member found: {}", member);
            }
            return member;
        } catch (SQLException e) {
            logger.error("Error finding member by name: {}", e.getMessage());
            throw new RuntimeException("Error finding member", e);
        }
    }

    public List<Member> getAllMembers() {
        try {
            List<Member> members = memberRepository.getAllMembers();
            logger.info("Retrieved {} members", members.size());
            return members;
        } catch (SQLException e) {
            logger.error("Error getting all members: {}", e.getMessage());
            throw new RuntimeException("Error getting all members", e);
        }
    }

    public Member updateMember(int memberId, String name, int gender, String remark) {
        try {
            boolean updated = memberRepository.updateMember(memberId, name, gender, remark);
            if (!updated) {
                logger.warn("Member not found for update with ID: {}", memberId);
                return null;
            }
            Member member = memberRepository.findMemberById(memberId);
            logger.info("Member updated: {}", member);
            return member;
        } catch (SQLException e) {
            logger.error("Error updating member: {}", e.getMessage());
            throw new RuntimeException("Error updating member", e);
        }
    }

    public boolean deleteMember(int memberId) {
        try {
            boolean deleted = memberRepository.deleteMember(memberId);
            if (deleted) {
                logger.info("Member deleted with ID: {}", memberId);
            } else {
                logger.warn("Member not found for deletion with ID: {}", memberId);
            }
            return deleted;
        } catch (SQLException e) {
            logger.error("Error deleting member: {}", e.getMessage());
            throw new RuntimeException("Error deleting member", e);
        }
    }
}
