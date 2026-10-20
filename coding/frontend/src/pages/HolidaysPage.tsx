import { useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { PageHeader } from '@/components/PageHeader';
import { ProblemAlert } from '@/components/AsyncState';
import { StatusTag } from '@/components/StatusTag';
import {
  useCreateHoliday,
  useDeleteHoliday,
  useHolidays,
  useUpdateHoliday,
} from '@/features/holidays/api';
import { useAuth } from '@/auth/AuthProvider';
import { toApiDate } from '@/lib/dates';
import { problemMessage } from '@/lib/errors';
import type { Holiday, HolidayType } from '@/types/api';

const YEARS = [2025, 2026, 2027];

interface HolidayFormValues {
  date: string;
  name: string;
  type: HolidayType;
}

function HolidayModal({
  open,
  holiday,
  onClose,
}: {
  open: boolean;
  holiday: Holiday | null;
  onClose: () => void;
}) {
  const { message } = App.useApp();
  const [values, setValues] = useState<HolidayFormValues>({
    date: dayjs().format('YYYY-MM-DD'),
    name: '',
    type: 'NATIONAL',
  });
  const createHoliday = useCreateHoliday();
  const updateHoliday = useUpdateHoliday();

  useEffect(() => {
    if (!open) return;
    setValues({
      date: holiday?.date ?? dayjs().format('YYYY-MM-DD'),
      name: holiday?.name ?? '',
      type: holiday?.type ?? 'NATIONAL',
    });
  }, [open, holiday]);

  const save = async () => {
    if (!values.name.trim()) {
      message.error('Name is required');
      return;
    }
    try {
      if (holiday) {
        await updateHoliday.mutateAsync({ id: holiday.id, input: values });
        message.success('Holiday updated');
      } else {
        await createHoliday.mutateAsync(values);
        message.success('Holiday created');
      }
      onClose();
    } catch (error) {
      message.error(problemMessage(error));
    }
  };

  return (
    <Modal
      title={holiday ? `Edit ${holiday.name}` : 'Add holiday'}
      open={open}
      onCancel={onClose}
      onOk={() => void save()}
      okText={holiday ? 'Save' : 'Add holiday'}
      confirmLoading={createHoliday.isPending || updateHoliday.isPending}
    >
      <Form layout="vertical">
        <Form.Item label="Date" required>
          <DatePicker
            value={values.date ? dayjs(values.date) : null}
            onChange={(value) => setValues((current) => ({ ...current, date: toApiDate(value) ?? '' }))}
            format="DD MMM YYYY"
            className="full-width"
          />
        </Form.Item>
        <Form.Item label="Name" required>
          <Input
            value={values.name}
            maxLength={150}
            onChange={(event) => setValues((current) => ({ ...current, name: event.target.value }))}
            placeholder="e.g. Independence Day"
          />
        </Form.Item>
        <Form.Item label="Type" required>
          <Select<HolidayType>
            value={values.type}
            onChange={(value) => setValues((current) => ({ ...current, type: value }))}
            options={[
              { value: 'NATIONAL', label: 'National holiday' },
              { value: 'JOINT_LEAVE', label: 'Joint leave' },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

export function HolidaysPage() {
  const { isAdmin } = useAuth();
  const { message, modal } = App.useApp();
  const [year, setYear] = useState<number | null>(2026);
  const [page, setPage] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Holiday | null>(null);
  const holidaysQuery = useHolidays(year, page);
  const deleteHoliday = useDeleteHoliday();

  const remove = (holiday: Holiday) => {
    modal.confirm({
      title: `Delete ${holiday.name}?`,
      content: 'Timelines that used this date keep their published snapshots; new calculations will no longer treat it as a holiday.',
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteHoliday.mutateAsync(holiday.id);
          message.success('Holiday deleted');
        } catch (error) {
          message.error(problemMessage(error));
          throw error;
        }
      },
    });
  };

  const columns: ColumnsType<Holiday> = [
    {
      title: 'Date',
      dataIndex: 'date',
      width: 170,
      render: (value: string) => dayjs(value).format('DD MMM YYYY'),
    },
    { title: 'Name', dataIndex: 'name' },
    {
      title: 'Type',
      dataIndex: 'type',
      width: 170,
      render: (value: HolidayType) => <StatusTag value={value} />,
    },
    ...(isAdmin
      ? [
          {
            title: '',
            key: 'actions',
            width: 110,
            render: (_: unknown, record: Holiday) => (
              <Space>
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  aria-label={`Edit ${record.name}`}
                  onClick={() => {
                    setEditing(record);
                    setModalOpen(true);
                  }}
                />
                <Button
                  type="text"
                  danger
                  icon={<DeleteOutlined />}
                  aria-label={`Delete ${record.name}`}
                  onClick={() => remove(record)}
                />
              </Space>
            ),
          },
        ]
      : []),
  ];

  return (
    <div>
      <PageHeader
        title="Holidays"
        subtitle="Weekends are excluded automatically. Holidays here are excluded from timeline working-day and plan calculations."
        extra={
          isAdmin ? (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null);
                setModalOpen(true);
              }}
            >
              Add holiday
            </Button>
          ) : undefined
        }
      />
      <Card>
        <Space wrap className="filter-bar">
          <Select
            value={year ?? 'all'}
            onChange={(value) => {
              setYear(value === 'all' ? null : (value as number));
              setPage(0);
            }}
            options={[
              { value: 'all', label: 'All covered years' },
              ...YEARS.map((value) => ({ value, label: String(value) })),
            ]}
            className="filter-select"
          />
          <Tag>{holidaysQuery.data?.totalElements ?? 0} entries</Tag>
          <Typography.Text type="secondary" className="small-text">
            Covered horizon: 2025–2027
          </Typography.Text>
        </Space>
        {holidaysQuery.error ? <ProblemAlert error={holidaysQuery.error} /> : null}
        <Table<Holiday>
          rowKey="id"
          columns={columns}
          dataSource={holidaysQuery.data?.content ?? []}
          loading={holidaysQuery.isLoading}
          scroll={{ x: 560 }}
          locale={{ emptyText: 'No holidays for this year.' }}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: holidaysQuery.data?.totalElements ?? 0,
            showSizeChanger: false,
            showTotal: (total) => `${total} entries`,
            onChange: (nextPage) => setPage(nextPage - 1),
          }}
        />
      </Card>
      <HolidayModal open={modalOpen} holiday={editing} onClose={() => setModalOpen(false)} />
    </div>
  );
}
