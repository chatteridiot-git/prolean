import { useState } from 'react';
import {
  App,
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  HistoryOutlined,
  PlusOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { useProjectContext } from '@/app/ProjectLayout';
import { AsyncState } from '@/components/AsyncState';
import { PageHeader } from '@/components/PageHeader';
import { PercentBar } from '@/components/PercentBar';
import { StatusTag } from '@/components/StatusTag';
import {
  useCancelTimelineDraft,
  useChangeTimelineProgress,
  useChangeTimelineStatus,
  useCreateTimeline,
  useCreateTimelineRevision,
  useEditTimelineDraft,
  usePublishTimeline,
  useTimeline,
  useTimelineVersions,
} from '@/features/timeline/api';
import { TimelineFormDrawer } from '@/features/timeline/TimelineFormDrawer';
import type { TimelineFormMode } from '@/features/timeline/TimelineFormDrawer';
import { formatDate, formatDateTime } from '@/lib/dates';
import { problemCode, problemMessage } from '@/lib/errors';
import { formatPercent, gapLabel } from '@/lib/progress';
import type { OperationalStatus, TimelineAggregate, TimelineVersion } from '@/types/api';
import type { TimelineValues } from '@/lib/validation';

export function TimelinePage() {
  const { project } = useProjectContext();
  const { message, modal } = App.useApp();
  const archived = project.status === 'ARCHIVED';
  const timelineQuery = useTimeline(project.id);
  const [historyPage, setHistoryPage] = useState(0);
  const versionsQuery = useTimelineVersions(project.id, historyPage);
  const createTimeline = useCreateTimeline(project.id);
  const editDraft = useEditTimelineDraft(project.id);
  const cancelDraft = useCancelTimelineDraft(project.id);
  const publishTimeline = usePublishTimeline(project.id);
  const createRevision = useCreateTimelineRevision(project.id);
  const changeStatus = useChangeTimelineStatus(project.id);
  const changeProgress = useChangeTimelineProgress(project.id);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<TimelineFormMode>('create');
  const [progressOpen, setProgressOpen] = useState(false);
  const [progressValue, setProgressValue] = useState(0);
  const [conflict, setConflict] = useState<{
    title: string;
    expected: string;
    actual: string;
    reapply: () => Promise<void>;
  } | null>(null);
  const [conflictPending, setConflictPending] = useState(false);
  const [conflictError, setConflictError] = useState<string | null>(null);

  const timeline = timelineQuery.data;
  const current = timeline?.current ?? null;
  const draft = timeline?.draft ?? null;

  const handleError = (error: unknown, fallback: string) => {
    message.error(problemMessage(error) || fallback);
  };

  /**
   * Turns a stale-timestamp conflict into an explicit review-and-reapply step. The stale values
   * are never silently overwritten: the user sees both timestamps and chooses to reload.
   */
  const offerConflictRecovery = async (
    error: unknown,
    title: string,
    expected: string | undefined,
    reapply: (fresh: TimelineAggregate) => Promise<void>,
  ): Promise<boolean> => {
    const code = problemCode(error);
    if (code !== 'DRAFT_MODIFIED' && code !== 'CURRENT_VERSION_CHANGED') return false;
    const refreshed = await timelineQuery.refetch();
    const fresh = refreshed.data;
    if (!fresh) {
      message.error('The timeline changed, but the latest values could not be loaded. Retry in a moment.');
      return true;
    }
    const actual = code === 'DRAFT_MODIFIED'
      ? fresh.draft?.updatedAt
      : fresh.current?.updatedAt;
    setConflictError(null);
    setConflictPending(false);
    setConflict({
      title,
      expected: expected ?? 'unknown',
      actual: actual ?? 'unknown',
      reapply: () => reapply(fresh),
    });
    return true;
  };

  const applyConflictResolution = async () => {
    if (!conflict || conflictPending) return;
    setConflictPending(true);
    setConflictError(null);
    try {
      await conflict.reapply();
      message.success('Latest timeline reloaded and your changes reapplied');
      setConflict(null);
      setDrawerOpen(false);
    } catch (error) {
      setConflictError(problemMessage(error) || 'Could not reapply the timeline changes.');
    } finally {
      setConflictPending(false);
    }
  };

  const discardConflictChanges = () => {
    setConflict(null);
    setConflictError(null);
    setDrawerOpen(false);
    message.info('Kept the latest timeline and discarded your unsaved changes.');
  };

  const submitForm = async (values: TimelineValues) => {
    try {
      if (drawerMode === 'create') {
        await createTimeline.mutateAsync(values);
        message.success('Timeline draft created');
      } else if (drawerMode === 'edit' && draft) {
        try {
          await editDraft.mutateAsync({
            ...values,
            expectedDraftUpdatedAt: draft.updatedAt,
          });
          message.success('Draft updated');
        } catch (error) {
          const recovering = await offerConflictRecovery(
            error,
            'Draft changed while you were editing',
            draft.updatedAt,
            async (fresh) => {
              if (!fresh.draft) throw error;
              await editDraft.mutateAsync({
                ...values,
                expectedDraftUpdatedAt: fresh.draft.updatedAt,
              });
            },
          );
          if (!recovering) throw error;
          return;
        }
      } else if (drawerMode === 'revise') {
        await createRevision.mutateAsync(values);
        message.success('Draft revision created');
      }
      setDrawerOpen(false);
    } catch (error) {
      handleError(error, 'Could not save the timeline.');
      throw error;
    }
  };

  const confirmPublish = () => {
    if (!draft) return;
    modal.confirm({
      title: `Publish version ${draft.versionNumber}?`,
      content: (
        <Space direction="vertical" size={6}>
          <span>
            Operational status and actual progress are carried forward to the new published version.
          </span>
          <span>
            The current published version is snapshotted and superseded. Published versions are never
            deleted.
          </span>
        </Space>
      ),
      okText: 'Publish',
      onOk: async () => {
        try {
          await publishTimeline.mutateAsync({
            expectedDraftUpdatedAt: draft.updatedAt,
            expectedCurrentUpdatedAt: current?.updatedAt,
          });
          message.success('Timeline published');
        } catch (error) {
          const recovering = await offerConflictRecovery(
            error,
            'Timeline changed before publishing',
            draft.updatedAt,
            async (fresh) => {
              if (!fresh.draft) throw error;
              await publishTimeline.mutateAsync({
                expectedDraftUpdatedAt: fresh.draft.updatedAt,
                expectedCurrentUpdatedAt: fresh.current?.updatedAt,
              });
            },
          );
          if (!recovering) {
            handleError(error, 'Could not publish the timeline.');
            throw error;
          }
        }
      },
    });
  };

  const confirmCancel = () => {
    if (!draft) return;
    modal.confirm({
      title: 'Cancel this draft?',
      content:
        timeline?.versionCount === 1
          ? 'This is the initial draft. Cancelling removes the timeline so a new one can be created later.'
          : 'The draft is cancelled and kept in the version history with frozen values.',
      okText: 'Cancel draft',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await cancelDraft.mutateAsync(draft.updatedAt);
          message.success('Draft cancelled');
        } catch (error) {
          handleError(error, 'Could not cancel the draft.');
          throw error;
        }
      },
    });
  };

  const changeOperationalStatus = async (status: OperationalStatus) => {
    try {
      await changeStatus.mutateAsync(status);
      message.success(
        status === 'CLOSED' ? 'Timeline closed at 100%' : 'Timeline status updated',
      );
    } catch (error) {
      handleError(error, 'Could not change the timeline status.');
    }
  };

  const submitProgress = async () => {
    try {
      await changeProgress.mutateAsync(progressValue);
      message.success('Actual progress updated');
      setProgressOpen(false);
    } catch (error) {
      handleError(error, 'Could not update the progress.');
    }
  };

  const historyColumns: ColumnsType<TimelineVersion> = [
    {
      title: 'Version',
      dataIndex: 'versionNumber',
      width: 90,
      render: (value: number) => <Tag color="geekblue">v{value}</Tag>,
    },
    {
      title: 'Lifecycle',
      dataIndex: 'lifecycleStatus',
      width: 130,
      render: (value: TimelineVersion['lifecycleStatus']) => <StatusTag value={value} />,
    },
    {
      title: 'Period',
      key: 'period',
      render: (_, record) => `${formatDate(record.startDate)} – ${formatDate(record.endDate)}`,
    },
    {
      title: 'Workdays',
      dataIndex: 'durationWorkdays',
      width: 100,
    },
    {
      title: 'Operational',
      dataIndex: 'operationalStatus',
      width: 130,
      render: (value: OperationalStatus) => <StatusTag value={value} />,
    },
    {
      title: 'Plan / actual',
      key: 'percent',
      width: 190,
      render: (_, record) => (
        <span>
          {formatPercent(record.percentPlan)} / {formatPercent(record.percentActual)}
        </span>
      ),
    },
    {
      title: 'Published',
      dataIndex: 'publishedAt',
      responsive: ['lg'],
      render: (value?: string) => formatDateTime(value),
    },
  ];

  return (
    <div>
      <PageHeader
        title="Timeline"
        subtitle="Draft, publish, and revise working-day timelines. Published versions are immutable history."
        extra={
          !timeline && !timelineQuery.isLoading ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={archived}
              onClick={() => {
                setDrawerMode('create');
                setDrawerOpen(true);
              }}
            >
              Create timeline
            </Button>
          ) : null
        }
      />
      <AsyncState
        loading={timelineQuery.isLoading}
        error={timelineQuery.error}
        onRetry={() => void timelineQuery.refetch()}
      >
        {!timeline ? (
          <Card>
            <Typography.Title level={4}>No timeline yet</Typography.Title>
            <Typography.Paragraph type="secondary">
              Create the first draft to plan the project period. The duration excludes weekends and
              Indonesian holidays.
            </Typography.Paragraph>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={archived}
              onClick={() => {
                setDrawerMode('create');
                setDrawerOpen(true);
              }}
            >
              Create timeline
            </Button>
          </Card>
        ) : (
          <>
            <Row gutter={[16, 16]}>
              <Col xs={24} xl={12}>
                <Card
                  title={
                    <Space>
                      <HistoryOutlined />
                      <span>Current published</span>
                      {current ? <Tag color="geekblue">v{current.versionNumber}</Tag> : null}
                    </Space>
                  }
                  extra={current ? <StatusTag value={current.operationalStatus} /> : null}
                  className="timeline-card"
                >
                  {current ? (
                    <Space direction="vertical" size="middle" className="full-width">
                      {current.planWarning === 'NO_WORKING_DAYS' ? (
                        <Alert
                          type="warning"
                          showIcon
                          message="This range has no working days. Plan percentage stays at 0.00%."
                        />
                      ) : null}
                      <Descriptions column={1} size="small" bordered>
                        <Descriptions.Item label="Period">
                          {formatDate(current.startDate)} – {formatDate(current.endDate)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Working days">
                          {current.durationWorkdays}
                        </Descriptions.Item>
                        <Descriptions.Item label="Plan as of">
                          {formatDate(current.planAsOfDate)} · {formatPercent(current.percentPlan)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Actual">
                          {formatPercent(current.percentActual)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Variance">
                          {gapLabel(current.percentActual, current.percentPlan)}
                        </Descriptions.Item>
                      </Descriptions>
                      <PercentBar
                        percent={current.percentActual}
                        status={current.operationalStatus}
                        label={`Actual vs ${formatPercent(current.percentPlan)} plan`}
                      />
                      <Space wrap>
                        <Select<OperationalStatus>
                          value={current.operationalStatus}
                          className="status-select"
                          disabled={archived || changeStatus.isPending}
                          onChange={(value) => void changeOperationalStatus(value)}
                          options={[
                            { value: 'OPEN', label: 'Open' },
                            { value: 'IN_PROGRESS', label: 'In progress' },
                            { value: 'CLOSED', label: 'Closed' },
                          ]}
                        />
                        <Button
                          icon={<EditOutlined />}
                          disabled={
                            archived || current.operationalStatus === 'CLOSED'
                          }
                          onClick={() => {
                            setProgressValue(current.percentActual);
                            setProgressOpen(true);
                          }}
                        >
                          Update progress
                        </Button>
                        <Button
                          icon={<RocketOutlined />}
                          disabled={archived || Boolean(draft)}
                          onClick={() => {
                            setDrawerMode('revise');
                            setDrawerOpen(true);
                          }}
                        >
                          New revision
                        </Button>
                      </Space>
                      {current.operationalStatus === 'CLOSED' ? (
                        <Typography.Text type="secondary">
                          Closed timelines are locked at 100% actual progress until reopened.
                        </Typography.Text>
                      ) : null}
                    </Space>
                  ) : (
                    <Typography.Paragraph type="secondary">
                      No published version yet. Publish the draft to set the baseline.
                    </Typography.Paragraph>
                  )}
                </Card>
              </Col>
              <Col xs={24} xl={12}>
                <Card
                  title={
                    <Space>
                      <EditOutlined />
                      <span>Draft</span>
                      {draft ? <Tag color="orange">v{draft.versionNumber}</Tag> : null}
                    </Space>
                  }
                  extra={draft ? <StatusTag value={draft.lifecycleStatus} /> : null}
                  className="timeline-card"
                >
                  {draft ? (
                    <Space direction="vertical" size="middle" className="full-width">
                      {draft.planWarning === 'NO_WORKING_DAYS' ? (
                        <Alert
                          type="warning"
                          showIcon
                          message="This draft range has no working days."
                        />
                      ) : null}
                      <Descriptions column={1} size="small" bordered>
                        <Descriptions.Item label="Period">
                          {formatDate(draft.startDate)} – {formatDate(draft.endDate)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Working days">
                          {draft.durationWorkdays}
                        </Descriptions.Item>
                        <Descriptions.Item label="Notes">
                          {draft.revisionNotes || '—'}
                        </Descriptions.Item>
                        <Descriptions.Item label="Last updated">
                          {formatDateTime(draft.updatedAt)}
                        </Descriptions.Item>
                      </Descriptions>
                      <Space wrap>
                        <Button
                          icon={<EditOutlined />}
                          disabled={archived}
                          onClick={() => {
                            setDrawerMode('edit');
                            setDrawerOpen(true);
                          }}
                        >
                          Edit draft
                        </Button>
                        <Button
                          type="primary"
                          icon={<RocketOutlined />}
                          disabled={archived}
                          onClick={confirmPublish}
                        >
                          Publish
                        </Button>
                        <Button danger icon={<CloseCircleOutlined />} disabled={archived} onClick={confirmCancel}>
                          Cancel draft
                        </Button>
                      </Space>
                      <Typography.Text type="secondary" className="small-text">
                        Publishing carries forward operational status and actual progress, then
                        snapshots the outgoing version.
                      </Typography.Text>
                    </Space>
                  ) : (
                    <Typography.Paragraph type="secondary">
                      No draft in progress. Use <strong>New revision</strong> on the published version
                      to plan changes.
                    </Typography.Paragraph>
                  )}
                </Card>
              </Col>
            </Row>

            <Card
              title={
                <Space>
                  <CheckCircleOutlined />
                  <span>Version history</span>
                </Space>
              }
              className="section-gap"
            >
              {versionsQuery.error ? (
                <Alert type="error" showIcon message={problemMessage(versionsQuery.error)} />
              ) : null}
              <Table<TimelineVersion>
                rowKey="id"
                columns={historyColumns}
                dataSource={versionsQuery.data?.content ?? []}
                loading={versionsQuery.isLoading}
                scroll={{ x: 860 }}
                locale={{ emptyText: 'No versions yet.' }}
                pagination={{
                  current: historyPage + 1,
                  pageSize: 50,
                  total: versionsQuery.data?.totalElements ?? 0,
                  showSizeChanger: false,
                  showTotal: (total) => `${total} versions`,
                  onChange: (nextPage) => setHistoryPage(nextPage - 1),
                }}
              />
            </Card>
          </>
        )}
      </AsyncState>
      <TimelineFormDrawer
        open={drawerOpen}
        mode={drawerMode}
        version={drawerMode === 'edit' ? draft : current}
        submitting={
          createTimeline.isPending ||
          editDraft.isPending ||
          createRevision.isPending
        }
        onClose={() => setDrawerOpen(false)}
        onSubmit={submitForm}
      />
      <Modal
        title="Update actual progress"
        open={progressOpen}
        onCancel={() => setProgressOpen(false)}
        onOk={() => void submitProgress()}
        okText="Save progress"
        confirmLoading={changeProgress.isPending}
      >
        <Typography.Paragraph type="secondary">
          Actual progress can be set from 0 to 100. Closing the timeline forces 100%.
        </Typography.Paragraph>
        <InputNumber
          min={0}
          max={100}
          addonAfter="%"
          value={progressValue}
          onChange={(value) => setProgressValue(Number(value ?? 0))}
          className="full-width"
        />
      </Modal>
      <Modal
        open={Boolean(conflict)}
        title={conflict?.title}
        okText="Reload and reapply"
        cancelText="Keep latest, discard my changes"
        confirmLoading={conflictPending}
        okButtonProps={{ disabled: conflictPending }}
        onOk={() => void applyConflictResolution()}
        onCancel={discardConflictChanges}
      >
        {conflictError ? (
          <Alert type="error" showIcon message={conflictError} className="conflict-error" />
        ) : null}
        <Alert
          type="warning"
          showIcon
          message="Your copy is based on an older version. Nothing was overwritten."
          description={
            <div>
              <div>Your expected timestamp: <code>{conflict?.expected}</code></div>
              <div>Current timestamp: <code>{conflict?.actual}</code></div>
              <p>
                Reload applies your entered values on top of the latest timeline. Review the
                current values first if another person may have changed the dates or notes.
              </p>
            </div>
          }
        />
      </Modal>
    </div>
  );
}
