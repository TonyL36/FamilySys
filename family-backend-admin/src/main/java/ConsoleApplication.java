import model.Member;
import model.Relationship;
import repository.MemberRepository;
import repository.RelationshipRepository;
import service.MemberService;
import service.RelationshipService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Scanner;

public class ConsoleApplication {
    private static final Logger logger = LogManager.getLogger(ConsoleApplication.class);
    private static final Scanner scanner = new Scanner(System.in);
    private static final MemberRepository memberRepository = new MemberRepository();
    private static final MemberService memberService = new MemberService(memberRepository);
    private static final RelationshipRepository relationshipRepository = new RelationshipRepository(memberRepository);
    private static final RelationshipService relationshipService = new RelationshipService(relationshipRepository, memberRepository);

    public static void main(String[] args) {
        while (true) {
            printMainMenu();
            int choice = getValidChoice(1, 3);
            if (choice == 3) {
                logger.info("Exiting application");
                break;
            }
            processMainChoice(choice);
        }
        scanner.close();
    }

    private static void printMainMenu() {
        System.out.println("\n1. Manage Members");
        System.out.println("2. Manage Relationships");
        System.out.println("3. Exit");
        System.out.print("Choose an option: ");
    }

    private static int getValidChoice(int min, int max) {
        while (true) {
            try {
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    System.out.println("Input cannot be empty. Please enter a number between " + min + " and " + max + ":");
                    continue;
                }
                int choice = Integer.parseInt(input);
                if (choice >= min && choice <= max) {
                    return choice;
                } else {
                    System.out.println("Please enter a number between " + min + " and " + max + ":");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number between " + min + " and " + max + ":");
            }
        }
    }

    private static String getValidString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("Input cannot be empty. Please try again.");
        }
    }

    private static void processMainChoice(int choice) {
        try {
            switch (choice) {
                case 1:
                    manageMember();
                    break;
                case 2:
                    manageRelationship();
                    break;
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } catch (Exception e) {
            logger.error("Error processing choice: {}", e.getMessage());
            System.out.println("An error occurred. Please try again.");
        }
    }

    private static void manageMember() {
        while (true) {
            printMemberMenu();
            int choice = getValidChoice(1, 6);
            if (choice == 6) {
                break;
            }
            processMemberChoice(choice);
        }
    }

    private static void printMemberMenu() {
        System.out.println("\n1. Search for a member");
        System.out.println("2. Print all members");
        System.out.println("3. Add a new member");
        System.out.println("4. Delete a member");
        System.out.println("5. Exit");
        System.out.println("6. Return to main menu");
        System.out.print("Choose an option: ");
    }

    private static void processMemberChoice(int choice) {
        try {
            switch (choice) {
                case 1:
                    searchMember();
                    break;
                case 2:
                    listAllMembers();
                    break;
                case 3:
                    addNewMember();
                    break;
                case 4:
                    deleteMember();
                    break;
                case 5:
                    System.out.println("Exiting program.");
                    System.exit(0);
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } catch (Exception e) {
            logger.error("Error processing member choice: {}", e.getMessage());
            System.out.println("An error occurred. Please try again.");
        }
    }

    private static void searchMember() {
        try {
            String name = getValidString("Enter member name to search: ");
            Member member = memberService.findMemberByName(name);
            if (member != null) {
                System.out.println("Found member:");
                printMember(member);
            } else {
                System.out.println("Member not found.");
            }
        } catch (Exception e) {
            logger.error("Error searching member: {}", e.getMessage());
            System.out.println("An error occurred while searching for the member.");
        }
    }

    private static void listAllMembers() {
        try {
            List<Member> members = memberService.getAllMembers();
            if (members.isEmpty()) {
                System.out.println("No members found.");
            } else {
                System.out.println("All members:");
                for (Member member : members) {
                    printMember(member);
                }
            }
        } catch (Exception e) {
            logger.error("Error listing members: {}", e.getMessage());
            System.out.println("An error occurred while listing members.");
        }
    }

    private static void addNewMember() {
        try {
            String name = getValidString("Enter member name: ");

            System.out.print("Enter generation: ");
            int generation = getValidChoice(0, Integer.MAX_VALUE);

            System.out.print("Enter gender (0 for Male, 1 for Female): ");
            int gender = getValidChoice(0, 1);

            Member newMember = memberService.addMember(name, generation, gender);
            if (newMember != null) {
                System.out.println("New member added:");
                printMember(newMember);
            } else {
                System.out.println("Failed to add member.");
            }
        } catch (Exception e) {
            logger.error("Error adding member: {}", e.getMessage());
            System.out.println("An error occurred while adding the member.");
        }
    }

    private static void deleteMember() {
        try {
            System.out.print("Enter member ID to delete: ");
            int id = getValidChoice(1, Integer.MAX_VALUE);
            boolean deleted = memberService.deleteMember(id);
            if (deleted) {
                System.out.println("Member deleted successfully.");
            } else {
                System.out.println("Member not found or could not be deleted.");
            }
        } catch (Exception e) {
            logger.error("Error deleting member: {}", e.getMessage());
            System.out.println("An error occurred while deleting the member.");
        }
    }

    private static void printMember(Member member) {
        System.out.println("Member ID: " + member.getMemberID());
        System.out.println("Name: " + member.getName());
        System.out.println("Generation: " + member.getGeneration());
        System.out.println("Gender: " + (member.getGender() == 0 ? "Male" : "Female"));
        System.out.println("--------------------");
    }

    private static void manageRelationship() {
        while (true) {
            printRelationshipMenu();
            int choice = getValidChoice(1, 6);
            if (choice == 5) {
                break;
            }
            processRelationshipChoice(choice);
        }
    }

    private static void printRelationshipMenu() {
        System.out.println("\n1. Add a new relationship");
        System.out.println("2. View relationships for a member");
        System.out.println("3. View all relationships");
        System.out.println("4. View relationships by relation ID");
        System.out.println("5. Return to main menu");
        System.out.println("6. Exit");
        System.out.print("Choose an option: ");
    }

    private static void processRelationshipChoice(int choice) {
        try {
            switch (choice) {
                case 1:
                    addNewRelationship();
                    break;
                case 2:
                    viewRelationshipsForMember();
                    break;
                case 3:
                    viewAllRelationships();
                    break;
                case 4:
                    viewRelationshipById();
                    break;
                case 6:
                    System.out.println("Exiting program.");
                    System.exit(0);
                default:
                    System.out.println("Invalid option. Please try again.");
            }
        } catch (Exception e) {
            logger.error("Error processing relationship choice: {}", e.getMessage());
            System.out.println("An error occurred. Please try again.");
        }
    }

    private static void addNewRelationship() {
        try {
            System.out.print("Enter ID of the first member: ");
            int member1ID = getValidChoice(1, Integer.MAX_VALUE);

            System.out.print("Enter ID of the second member: ");
            int member2ID = getValidChoice(1, Integer.MAX_VALUE);

            if (member1ID == member2ID) {
                System.out.println("A member cannot have a relationship with themselves.");
                return;
            }

            displayRelationshipOptions();
            System.out.print("Enter relationship type (1-32): ");
            int relationType = getValidChoice(1, 32);

            boolean success = relationshipService.addRelationship(member1ID, member2ID, relationType);
            if (success) {
                System.out.println("Relationship added successfully.");
            } else {
                System.out.println("Failed to add relationship. Please check if both members exist and the relationship is valid.");
            }
        } catch (Exception e) {
            logger.error("Error adding relationship: {}", e.getMessage());
            System.out.println("An error occurred while adding the relationship.");
        }
    }

    private static void displayRelationshipOptions() {
        System.out.println("Available relationships:");
        System.out.println("1: 丈夫\t2: 妻子\t5: 长子\t6: 次子");
        System.out.println("7: 小子\t8: 长女\t9: 次女\t10: 小女");
        System.out.println("15: 表哥\t16: 表姐\t17: 表弟\t18: 表妹");
    }

    private static void viewRelationshipsForMember() {
        try {
            System.out.print("Enter member ID: ");
            int memberID = getValidChoice(1, Integer.MAX_VALUE);
            List<Relationship> relationships = relationshipService.getRelationshipsForMember(memberID);

            Member member = memberService.findMemberById(memberID);
            if (member == null) {
                System.out.println("Member not found.");
                return;
            }

            if (relationships.isEmpty()) {
                System.out.println("No relationships found for this member.");
            } else {
                System.out.println("Relationships for " + member.getName() + " (ID: " + member.getMemberID() + "):");
                for (Relationship relationship : relationships) {
                    int relatedMemberID = (relationship.getMember1() == memberID) ?
                            relationship.getMember2() : relationship.getMember1();
                    Member relatedMember = memberService.findMemberById(relatedMemberID);
                    System.out.println(relationship.getRelationshipDescription() + ": " +
                            relatedMember.getName() + " (ID: " + relatedMember.getMemberID() + ")");
                }
            }
        } catch (Exception e) {
            logger.error("Error viewing relationships: {}", e.getMessage());
            System.out.println("An error occurred while viewing relationships.");
        }
    }

    private static void viewAllRelationships() {
        try {
            List<Relationship> relationships = relationshipService.getAllRelationships();
            if (relationships.isEmpty()) {
                System.out.println("No relationships found.");
            } else {
                System.out.println("All relationships:");
                for (Relationship relationship : relationships) {
                    Member member1 = memberService.findMemberById(relationship.getMember1());
                    Member member2 = memberService.findMemberById(relationship.getMember2());
                    System.out.println(member1.getName() + " (ID: " + member1.getMemberID() + ") 's " +
                            relationship.getRelationshipDescription() + " is " +
                            member2.getName() + " (ID: " + member2.getMemberID() + ")");
                }
            }
        } catch (Exception e) {
            logger.error("Error viewing relationships: {}", e.getMessage());
            System.out.println("An error occurred while viewing relationships.");
        }
    }

    private static void viewRelationshipById() {
        try {
            System.out.print("Enter relationship ID: ");
            int relationshipID = getValidChoice(1, Integer.MAX_VALUE);
            Relationship relationship = relationshipService.getRelationshipByRelationID(relationshipID);

            if (relationship != null) {
                Member member1 = memberService.findMemberById(relationship.getMember1());
                Member member2 = memberService.findMemberById(relationship.getMember2());
                System.out.println(member1.getName() + " (ID: " + member1.getMemberID() + ") 's " +
                        relationship.getRelationshipDescription() + " is " +
                        member2.getName() + " (ID: " + member2.getMemberID() + ")");
            } else {
                System.out.println("Relationship not found.");
            }
        } catch (Exception e) {
            logger.error("Error viewing relationship: {}", e.getMessage());
            System.out.println("An error occurred while viewing the relationship.");
        }
    }
}