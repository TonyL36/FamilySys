package model;

public class Relationship {
    private int relationID;
    private int member1;
    private int member2;
    private int relation;
    private String member1Name;
    private String member2Name;

    // 基本构造函数，用于数据库查询
    public Relationship(int relationID, int member1, int member2, int relation) {
        this.relationID = relationID;
        this.member1 = member1;
        this.member2 = member2;
        this.relation = relation;
    }

    // 带成员名字的构造函数，用于显示
    public Relationship(int relationID, int member1, int member2, int relation, String member1Name, String member2Name) {
        this(relationID, member1, member2, relation);
        this.member1Name = member1Name;
        this.member2Name = member2Name;
    }

    // Getters and setters
    public int getRelationID() { return relationID; }
    public void setRelationID(int relationID) { this.relationID = relationID; }
    public int getMember1() { return member1; }
    public void setMember1(int member1) { this.member1 = member1; }
    public int getMember2() { return member2; }
    public void setMember2(int member2) { this.member2 = member2; }
    public int getRelation() { return relation; }
    public void setRelation(int relation) { this.relation = relation; }
    public String getMember1Name() { return member1Name; }
    public void setMember1Name(String member1Name) { this.member1Name = member1Name; }
    public String getMember2Name() { return member2Name; }
    public void setMember2Name(String member2Name) { this.member2Name = member2Name; }

    public String getRelationshipDescription() {
        switch (relation) {
            case 1: return "丈夫";
            case 2: return "妻子";
            case 3: return "父亲";
            case 4: return "母亲";
            case 5: return "长子";
            case 6: return "次子";
            case 7: return "小子";
            case 8: return "长女";
            case 9: return "次女";
            case 10: return "小女";
            case 11: return "哥哥";
            case 12: return "姐姐";
            case 13: return "弟弟";
            case 14: return "妹妹";
            case 15: return "表哥";
            case 16: return "表姐";
            case 17: return "表弟";
            case 18: return "表妹";
            case 19: return "爷爷";
            case 20: return "奶奶";
            case 21: return "外祖母";
            case 22: return "外祖父";
            case 23: return "孙子";
            case 24: return "孙女";
            case 25: return "外孙";
            case 26: return "外孙女";
            case 27: return "岳父";
            case 28: return "岳母";
            case 29: return "公公";
            case 30: return "婆婆";
            case 31: return "儿媳";
            case 32: return "女婿";
            default: return "未知关系";
        }
    }

    @Override
    public String toString() {
        return "Relationship{" +
                "relationID=" + relationID +
                ", member1=" + member1 +
                (member1Name != null ? "(" + member1Name + ")" : "") +
                ", member2=" + member2 +
                (member2Name != null ? "(" + member2Name + ")" : "") +
                ", relation=" + relation +
                ", description='" + getRelationshipDescription() + '\'' +
                '}';
    }
}

