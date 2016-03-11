/**
 * Copyright (c) 2016 LARUS Business Automation [http://www.larus-ba.it]
 * <p>
 * This file is part of the "LARUS Integration Framework for Neo4j".
 * <p>
 * The "LARUS Integration Framework for Neo4j" is licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Created on 19/02/16
 */
package it.neo4j.jdbc.bolt;

import it.neo4j.jdbc.Connection;
import it.neo4j.jdbc.bolt.data.StatementData;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.neo4j.driver.v1.*;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

/**
 * @author AgileLARUS
 * @since 3.0.0
 */
@RunWith(PowerMockRunner.class) @PrepareForTest({ BoltStatement.class, BoltResultSet.class }) public class BoltStatementTest {

	@Rule public ExpectedException expectedEx = ExpectedException.none();

	private BoltResultSet mockedRS;

	private Session mockSessionOpen() {
		Session session = mock(Session.class);
		when(session.isOpen()).thenReturn(true);
		return session;
	}

	private BoltConnection mockConnectionOpen() throws SQLException {
		BoltConnection mockConnection = mock(BoltConnection.class);
		when(mockConnection.isClosed()).thenReturn(false);
		return mockConnection;
	}

	private BoltConnection mockConnectionClosed() throws SQLException {
		BoltConnection mockConnection = mock(BoltConnection.class);
		when(mockConnection.isClosed()).thenReturn(true);
		return mockConnection;
	}

	private BoltConnection mockConnectionOpenWithTransactionThatReturns(ResultCursor cur) throws SQLException {
		Transaction mockTransaction = mock(Transaction.class);
		when(mockTransaction.run(anyString())).thenReturn(cur);

		BoltConnection mockConnection = this.mockConnectionOpen();
		when(mockConnection.getTransaction()).thenReturn(mockTransaction);
		return mockConnection;
	}

	@Before public void mockStatics() {
		mockStatic(BoltResultSet.class);
		this.mockedRS = mock(BoltResultSet.class);
		PowerMockito.when(BoltResultSet.instantiate(anyObject(), anyBoolean())).thenReturn(mockedRS);
		PowerMockito.when(BoltResultSet.instantiate(anyObject(), anyBoolean(), any(int[].class))).thenReturn(mockedRS);
	}

	/*------------------------------*/
	/*             close            */
	/*------------------------------*/
	@Test public void closeShouldCloseExistingResultSet() throws Exception {

		doNothing().when(mockedRS).close();

		Statement statement = new BoltStatement(this.mockConnectionOpenWithTransactionThatReturns(null));

		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
		statement.close();

		verifyStatic(times(1));
		BoltResultSet.instantiate(null, false);

		verify(mockedRS, times(1)).close();
	}

	@Test public void closeShouldNotCallCloseOnAnyResultSet() throws Exception {

		Statement statement = new BoltStatement(this.mockConnectionOpenWithTransactionThatReturns(null));

		statement.close();

		verify(mockedRS, never()).close();
	}

	@Test public void closeMultipleTimesIsNOOP() throws Exception {

		Statement statement = new BoltStatement(this.mockConnectionOpenWithTransactionThatReturns(null));
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
		statement.close();
		statement.close();
		statement.close();

		verify(mockedRS, times(1)).close();
	}

	@Test public void closeShouldCloseTheTransactionNotCommiting() throws Exception {

		Transaction mockTransaction = mock(Transaction.class);

		BoltConnection mockConnection = this.mockConnectionOpen();
		when(mockConnection.getTransaction()).thenReturn(mockTransaction);

		Statement statement = new BoltStatement(mockConnection);

		statement.close();

		verify(mockTransaction, times(1)).failure();
		verify(mockTransaction, times(1)).close();
	}

	/*------------------------------*/
	/*           isClosed           */
	/*------------------------------*/
	@Test public void isClosedShouldReturnFalseWhenCreated() throws SQLException {
		Statement statement = new BoltStatement(mockConnectionOpen());

		assertFalse(statement.isClosed());
	}

	/*------------------------------*/
	/*          executeQuery        */
	/*------------------------------*/
	@Test public void executeQueryShouldThrowExceptionWhenClosedConnection() throws SQLException {
		expectedEx.expect(SQLException.class);

		Statement statement = new BoltStatement(this.mockConnectionClosed(), 0, 0, 0);
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
	}

	@Test public void executeQueryShouldReturnCorrectResultSetStructureConnectionNotAutocommit() throws Exception {
		BoltConnection mockConnection = mockConnectionOpenWithTransactionThatReturns(null);

		Statement statement = new BoltStatement(mockConnection, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);

		verifyStatic(times(1));
		BoltResultSet.instantiate(null, false, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
	}

	@Test public void executeQueryShouldThrowExceptionOnClosedStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		BoltStatement statement = mock(BoltStatement.class);
		when(statement.isClosed()).thenReturn(true);
		when(statement.executeQuery(anyString())).thenCallRealMethod();

		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
	}

	@Ignore @Test public void executeQueryShouldThrowExceptionOnPreparedStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		Connection connection = new BoltConnection(mockSessionOpen());
		Statement statement = connection.prepareStatement(null);
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
	}

	@Ignore @Test public void executeQueryShouldThrowExceptionOnCallableStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		Connection connection = new BoltConnection(mockSessionOpen());
		Statement statement = connection.prepareCall(null);
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
	}

	@Ignore @Test public void executeQueryShouldThrowExceptionOnTimeoutExceeded() throws SQLException {
		expectedEx.expect(SQLException.class);

		Transaction transaction = mock(Transaction.class);

		given(transaction.run(anyString())).willAnswer(invocation -> {
			Thread.sleep(1500);
			return null;
		});

		Session session = mock(Session.class);
		given(session.beginTransaction()).willReturn(transaction);
		given(session.isOpen()).willReturn(true);

		Statement statement = new BoltStatement(new BoltConnection(session), 0, 0, 0);

		statement.setQueryTimeout(1);
		statement.executeQuery(StatementData.STATEMENT_CREATE);

		fail();
	}


	/*------------------------------*/
	/*         executeUpdate        */
	/*------------------------------*/

	@Test public void executeUpdateShouldRun() throws SQLException {
		ResultCursor mockCursor = mock(ResultCursor.class);
		ResultSummary mockSummary = mock(ResultSummary.class);
		UpdateStatistics mockStats = mock(UpdateStatistics.class);
		when(mockCursor.summarize()).thenReturn(mockSummary);
		when(mockSummary.updateStatistics()).thenReturn(mockStats);
		when(mockStats.nodesCreated()).thenReturn(1);
		when(mockStats.nodesDeleted()).thenReturn(0);

		Statement statement = new BoltStatement(this.mockConnectionOpenWithTransactionThatReturns(mockCursor), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY, ResultSet.HOLD_CURSORS_OVER_COMMIT);
		statement.executeQuery(StatementData.STATEMENT_MATCH_ALL);
		statement.executeUpdate(StatementData.STATEMENT_CREATE);
	}

	@Test public void executeUpdateShouldThrowExceptionOnClosedStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		Connection connection = new BoltConnection(mockSessionOpen());
		Statement statement = connection.createStatement();
		statement.close();
		statement.executeUpdate(StatementData.STATEMENT_CREATE);
	}

	@Ignore @Test public void executeUpdateShouldThrowExceptionOnPreparedStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		Connection connection = new BoltConnection(mockSessionOpen());
		Statement statement = connection.prepareStatement(null);
		statement.executeUpdate(StatementData.STATEMENT_CREATE);
	}

	@Ignore @Test public void executeUpdateShouldThrowExceptionOnCallableStatement() throws SQLException {
		expectedEx.expect(SQLException.class);

		Connection connection = new BoltConnection(mockSessionOpen());
		Statement statement = connection.prepareCall(null);
		statement.executeUpdate(StatementData.STATEMENT_CREATE);
	}

	@Ignore @Test public void executeUpdateShouldThrowExceptionOnTimeoutExceeded() throws SQLException {
		expectedEx.expect(SQLException.class);

		Transaction transaction = mock(Transaction.class);

		given(transaction.run(anyString())).willAnswer(invocation -> {
			Thread.sleep(1500);
			return null;
		});

		Session session = mock(Session.class);
		given(session.beginTransaction()).willReturn(transaction);
		given(session.isOpen()).willReturn(true);

		Statement statement = new BoltStatement(new BoltConnection(session), 0, 0, 0);

		statement.setQueryTimeout(1);
		statement.executeUpdate(StatementData.STATEMENT_CREATE);

		fail();
	}
}
