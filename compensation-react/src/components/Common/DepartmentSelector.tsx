import React, { useState } from 'react';
import { Tag, Select, Space, Button, Tooltip } from 'antd';
import { PlusOutlined, ArrowUpOutlined, ArrowDownOutlined, DragOutlined } from '@ant-design/icons';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors, DragEndEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface DepartmentTagProps {
  dept: string;
  index: number;
  isMain: boolean;
  onRemove: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
  disabled: boolean;
  totalCount: number;
}

const DepartmentTag: React.FC<DepartmentTagProps> = ({
  dept,
  index,
  isMain,
  onRemove,
  onMoveUp,
  onMoveDown,
  disabled,
  totalCount,
}) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: dept });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
    >
      <Tag
        closable={!disabled}
        onClose={onRemove}
        style={{
          marginBottom: 4,
          marginRight: 4,
          padding: '4px 8px',
          border: isMain ? '2px solid #1890ff' : '1px solid #d9d9d9',
          cursor: isDragging ? 'grabbing' : 'grab',
          opacity: isDragging ? 0.8 : 1,
        }}
      >
        <Space size="small">
          {!disabled && (
            <DragOutlined
              {...listeners}
              style={{
                cursor: 'grab',
                color: '#999',
              }}
            />
          )}
          <span>{dept}</span>
          {isMain && <span style={{ color: '#1890ff', fontSize: '10px' }}>(主部门)</span>}
          {!disabled && (
            <Space size={0}>
              <Button
                type="text"
                size="small"
                icon={<ArrowUpOutlined />}
                disabled={index === 0}
                onClick={onMoveUp}
                style={{ padding: 0, minWidth: 16, height: 16 }}
              />
              <Button
                type="text"
                size="small"
                icon={<ArrowDownOutlined />}
                disabled={index === totalCount - 1}
                onClick={onMoveDown}
                style={{ padding: 0, minWidth: 16, height: 16 }}
              />
            </Space>
          )}
        </Space>
      </Tag>
    </div>
  );
};

interface DepartmentSelectorProps {
  departments: string[];
  onChange: (departments: string[]) => void;
  disabled?: boolean;
  size?: 'small' | 'middle' | 'large';
  placeholder?: string;
}

const DepartmentSelector: React.FC<DepartmentSelectorProps> = ({
  departments,
  onChange,
  disabled = false,
  size = 'middle',
  placeholder = '选择部门'
}) => {
  const [inputValue, setInputValue] = useState('');
  const [showInput, setShowInput] = useState(false);

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;

    if (active.id !== over?.id) {
      const oldIndex = departments.indexOf(active.id as string);
      const newIndex = departments.indexOf(over?.id as string);
      onChange(arrayMove(departments, oldIndex, newIndex));
    }
  };

  const handleAdd = () => {
    if (inputValue && !departments.includes(inputValue)) {
      onChange([...departments, inputValue]);
      setInputValue('');
      setShowInput(false);
    }
  };

  const handleRemove = (removedDept: string) => {
    onChange(departments.filter(dept => dept !== removedDept));
  };

  const handleMoveUp = (index: number) => {
    if (index > 0) {
      const newDepts = [...departments];
      [newDepts[index - 1], newDepts[index]] = [newDepts[index], newDepts[index - 1]];
      onChange(newDepts);
    }
  };

  const handleMoveDown = (index: number) => {
    if (index < departments.length - 1) {
      const newDepts = [...departments];
      [newDepts[index], newDepts[index + 1]] = [newDepts[index + 1], newDepts[index]];
      onChange(newDepts);
    }
  };

  return (
    <Space size="large" style={{ width: '100%' }}>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={departments} strategy={verticalListSortingStrategy}>
          <div>
            {departments.map((dept, index) => (
              <DepartmentTag
                key={dept}
                dept={dept}
                index={index}
                isMain={index === 0}
                onRemove={() => handleRemove(dept)}
                onMoveUp={() => handleMoveUp(index)}
                onMoveDown={() => handleMoveDown(index)}
                disabled={disabled}
                totalCount={departments.length}
              />
            ))}

            {!disabled && (
              showInput ? (
                <Space size="small">
                  <Select
                    style={{ width: 120 }}
                    size={size}
                    value={inputValue}
                    onChange={setInputValue}
                    onPressEnter={handleAdd}
                    placeholder={placeholder}
                    showSearch
                    allowClear
                    mode="tags"
                    open={false}
                    onFocus={() => setShowInput(true)}
                    onBlur={() => {
                      if (!inputValue) setShowInput(false);
                    }}
                  />
                  <Button size="small" type="primary" onClick={handleAdd}>
                    确定
                  </Button>
                  <Button size="small" onClick={() => { setInputValue(''); setShowInput(false); }}>
                    取消
                  </Button>
                </Space>
              ) : (
                <Tag
                  style={{ borderStyle: 'dashed', cursor: 'pointer' }}
                  onClick={() => setShowInput(true)}
                >
                  <PlusOutlined /> 添加部门
                </Tag>
              )
            )}
          </div>
        </SortableContext>
      </DndContext>

      {departments.length > 0 && (
        <div style={{ fontSize: '12px', color: '#666' }}>
          <Tooltip title="可以拖拽部门标签来调整顺序，或使用上下箭头按钮">
            💡 第一个部门为主部门，可拖拽调整顺序
          </Tooltip>
        </div>
      )}
    </Space>
  );
};

export default DepartmentSelector;