@E2E
Feature: Folder Management
  As an admin
  I want to manage folders within my teams
  So that I can organize my documents

  Scenario: Admin retrieves existing folders for a team
    Given the admin logs in with email "andrei.echipa@prodeng.ro" and password "password"
    When the admin retrieves all folders for team "team1"
    Then the response status code is 200
    And the client can see at least 1 folder

  Scenario: Admin creates a new root folder
    Given the admin logs in with email "andrei.echipa@prodeng.ro" and password "password"
    When the admin creates a team named "Test Team" with description "A team for testing"
    Then the response status code is 201
    When the admin creates a root folder named "E2E Automated Folder" in team "created_team"
    Then the response status code is 201