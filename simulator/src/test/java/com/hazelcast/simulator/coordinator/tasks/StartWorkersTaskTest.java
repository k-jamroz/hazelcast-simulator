package com.hazelcast.simulator.coordinator.tasks;

import com.hazelcast.simulator.agent.operations.CreateWorkerOperation;
import com.hazelcast.simulator.agent.workerprocess.WorkerParameters;
import com.hazelcast.simulator.coordinator.DeploymentPlan;
import com.hazelcast.simulator.coordinator.registry.AgentData;
import com.hazelcast.simulator.coordinator.registry.ComponentRegistry;
import com.hazelcast.simulator.coordinator.registry.WorkerData;
import com.hazelcast.simulator.protocol.CoordinatorClient;
import com.hazelcast.simulator.protocol.core.SimulatorAddress;
import com.hazelcast.simulator.utils.CommandLineExitException;
import com.hazelcast.simulator.vendors.StubVendorDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static com.hazelcast.simulator.coordinator.DeploymentPlan.createDeploymentPlan;
import static com.hazelcast.simulator.utils.CommonUtils.closeQuietly;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StartWorkersTaskTest {

    private final ComponentRegistry componentRegistry = new ComponentRegistry();
    private CoordinatorClient client;
    private AgentData agent1;
    private AgentData agent2;
    private AgentData agent3;

    @Before
    public void before() {
        client = mock(CoordinatorClient.class);

        agent1 = componentRegistry.addAgent("192.168.0.1", "192.168.0.1");
        agent2 = componentRegistry.addAgent("192.168.0.2", "192.168.0.2");
        agent3 = componentRegistry.addAgent("192.168.0.3", "192.168.0.3");
    }

    @After
    public void after() {
        closeQuietly(client);
    }

    @Test
    public void testCreateWorkers_withClients() throws Exception {
        Map<SimulatorAddress, List<WorkerParameters>> deploymentPlan = getDeployment(0, 6, 3);

        Future f = mock(Future.class);
        when(f.get()).thenReturn("SUCCESS");
        when(client.submit(eq(agent1.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);
        when(client.submit(eq(agent2.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);
        when(client.submit(eq(agent3.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);

        new StartWorkersTask(deploymentPlan, Collections.<String, String>emptyMap(), client, componentRegistry, 0).run();

        assertComponentRegistry(componentRegistry, 6, 3);
    }

    @Test
    public void testCreateWorkers_noClients() throws Exception {
        Map<SimulatorAddress, List<WorkerParameters>> deploymentPlan = getDeployment(0, 6, 0);

        Future f = mock(Future.class);
        when(f.get()).thenReturn("SUCCESS");
        when(client.submit(eq(agent1.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);
        when(client.submit(eq(agent2.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);
        when(client.submit(eq(agent3.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);

        new StartWorkersTask(deploymentPlan, Collections.<String, String>emptyMap(), client, componentRegistry, 0).run();

        assertComponentRegistry(componentRegistry, 6, 0);
    }

    @Test(expected = CommandLineExitException.class)
    public void testCreateWorkers_withErrorResponse() throws Exception {
        Map<SimulatorAddress, List<WorkerParameters>> deploymentPlan = getDeployment(0, 1, 0);

        Future f = mock(Future.class);
        when(f.get()).thenThrow(new ExecutionException(null));
        when(client.submit(eq(agent1.getAddress()), any(CreateWorkerOperation.class))).thenReturn(f);

        new StartWorkersTask(deploymentPlan, Collections.<String, String>emptyMap(), client, componentRegistry, 0).run();
    }

    private Map<SimulatorAddress, List<WorkerParameters>> getDeployment(int dedicatedMemberMachineCount,
                                                                        int memberWorkerCount,
                                                                        int clientWorkerCount) {
        StubVendorDriver vendorDriver = new StubVendorDriver();
        componentRegistry.assignDedicatedMemberMachines(dedicatedMemberMachineCount);

        DeploymentPlan deploymentPlan = createDeploymentPlan(
                componentRegistry, vendorDriver,
                "javaclient", memberWorkerCount, clientWorkerCount);

        return deploymentPlan.getWorkerDeployment();
    }

    private static void assertComponentRegistry(ComponentRegistry componentRegistry,
                                                int expectedMemberCount,
                                                int expectedClientCount) {
        int actualMemberCount = 0;
        int actualClientCount = 0;
        for (WorkerData workerData : componentRegistry.getWorkers()) {
            if (workerData.isMemberWorker()) {
                actualMemberCount++;
            } else {
                actualClientCount++;
            }
        }
        assertEquals(expectedMemberCount, actualMemberCount);
        assertEquals(expectedClientCount, actualClientCount);
    }
}
