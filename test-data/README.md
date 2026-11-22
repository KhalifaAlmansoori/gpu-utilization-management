# Sample test data

First launch starts an empty simulation automatically when `data/state.txt` does not exist
inside the included `data/` directory. [empty-state.txt](empty-state.txt) is an optional fixture
for the same state: no students/jobs and an unset clock. An unreadable or malformed existing
snapshot reports an error and is not replaced automatically.
Both supplied snapshots contain ASCII text and work with the reader/writer's
default character set. Copying either snapshot replaces your saved simulation.

The prepared [state.txt](state.txt) contains the demo data required by the project description:

- 5 approved students
- pending and rejected registration requests
- 4 GPUs
- 20 jobs across all states (queued, running, completed, cancelled)

Copy `state.txt` into `data/state.txt` before running to start the demo from this state.
This replaces the current simulation, so keep a copy of any save you want to retain.
The application loads this snapshot directly through `DataStore`. See the root README's
[loading instructions](../README.md#loading-the-sample-test-data).

- Administrator: `admin` / `gpu2026`
- Approved student IDs: `100000001` through `100000005`
- Password for all demo students: `student123`
- Simulation start: `21/11/2026 09:00`; current time: `21/11/2026 09:07`

The fixture includes queued and running cancellations, a recorded extra-time grant, and a
waiting queue behind four busy GPUs. All records were generated through the real services.
Its snapshot format is described in the [storage contract](../docs/model-api.md#storage-snapshot-and-screen-integration).
