import React from 'react';
import ResourceManager from './ResourceManager';

// Thin alias page for Admin Resource V2 controller
// Keeps routing explicit for the v2 endpoint while reusing the manager UI
const ResourcesV2Page: React.FC = () => {
  return <ResourceManager />;
};

export default ResourcesV2Page;
