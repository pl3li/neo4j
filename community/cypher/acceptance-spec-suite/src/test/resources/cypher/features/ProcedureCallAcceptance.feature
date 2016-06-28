#
# Copyright (c) 2002-2016 "Neo Technology,"
# Network Engine for Objects in Lund AB [http://neotechnology.com]
#
# This file is part of Neo4j.
#
# Neo4j is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

Feature: ProcedureCallAcceptance

  Background:
    Given an empty graph

  Scenario: Standalone call to procedure without arguments
   And there exists a procedure test.labels() :: (label :: STRING?):
     |label|
     |'A'  |
     |'B'  |
     |'C'  |
  When executing query:
    """
    CALL test.labels()
    """
  Then the result should be, in order:
    |label|
    |'A'  |
    |'B'  |
    |'C'  |

  Scenario: Standalone call to VOID procedure
    And there exists a procedure test.doNothing() :: VOID:
     |
    When executing query:
    """
    CALL test.doNothing()
    """
    Then the result should be empty

  Scenario: Standalone call to VOID procedure without arguments
    And there exists a procedure test.doNothing() :: VOID:
      |
    When executing query:
    """
    CALL test.doNothing
    """
    Then the result should be empty

  Scenario: Standalone call to empty procedure
    And there exists a procedure test.doNothing() :: ():
      |
    When executing query:
    """
    CALL test.doNothing()
    """
    Then the result should be empty

  Scenario: Standalone call to empty procedure without arguments
    And there exists a procedure test.doNothing() :: ():
      |
    When executing query:
    """
    CALL test.doNothing
    """
    Then the result should be empty
