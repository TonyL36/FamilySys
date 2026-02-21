// Member.java
package model;

public class Member {
    private int memberID;
    private String name;
    private int generation;
    private int gender; // 0=male,1=female
    private String remark;

    public Member(int memberID, String name, int generation, int gender) {
        this.memberID = memberID;
        this.name = name;
        this.generation = generation;
        this.gender = gender;
        this.remark = null;
    }

    public Member(int memberID, String name, int generation, int gender, String remark) {
        this.memberID = memberID;
        this.name = name;
        this.generation = generation;
        this.gender = gender;
        this.remark = remark;
    }

    // Getters and setters
    public int getMemberID() { return memberID; }
    public void setMemberID(int memberID) { this.memberID = memberID; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getGeneration() { return generation; }
    public void setGeneration(int generation) { this.generation = generation; }
    public int getGender() { return gender; }
    public void setGender(int gender) { this.gender = gender; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    @Override
    public String toString() {
        return String.format("Member{id=%d, name='%s', generation=%d, gender=%s, remark='%s'}",
                memberID, name, generation, gender == 0 ? "Male" : "Female", remark);
    }
}
