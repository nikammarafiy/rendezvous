package apps.jan.rendezvous;

/**
 * This is a class used to represent the relevant
 * information for a group member that exists in a
 * group on the client device.
 */
public class GroupMemberProfile {
    private String phone_number;
    private String regid;
    private String name;

    public GroupMemberProfile(String phone, String regid, String name){
        this.phone_number = phone;
        this.regid = regid;
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public String getRegid(){
        return this.regid;
    }

    public String getPhoneNumber(){
        return this.phone_number;
    }

}
