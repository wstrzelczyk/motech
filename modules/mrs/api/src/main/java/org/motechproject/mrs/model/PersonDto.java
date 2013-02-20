package org.motechproject.mrs.model;


import org.joda.time.DateTime;
import org.motechproject.mrs.domain.Attribute;
import org.motechproject.mrs.domain.Person;
import org.codehaus.jackson.annotate.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class PersonDto implements Person{
    private String personId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String preferredName;
    private String address;
    private DateTime dateOfBirth;
    private Boolean birthDateEstimated;
    private Integer age;
    private String gender;
    private Boolean dead;
    private List<Attribute> attributes = new ArrayList<Attribute>();
    private DateTime deathDate;


    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public void setPreferredName(String preferredName) {
        this.preferredName = preferredName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public DateTime getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(DateTime dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Boolean getBirthDateEstimated() {
        return birthDateEstimated;
    }

    public void setBirthDateEstimated(Boolean birthDateEstimated) {
        this.birthDateEstimated = birthDateEstimated;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Boolean isDead() {
        return dead;
    }

    public void setDead(Boolean dead) {
        this.dead = dead;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public DateTime getDeathDate() {
        return deathDate;
    }

    public void setDeathDate(DateTime deathDate) {
        this.deathDate = deathDate;
    }


}
