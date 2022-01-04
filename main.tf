# Provides an Elastic MapReduce Cluster with Hadoop installed
#
# Usage:
# Configure the credentials first with `aws configure`
# Create a file named `terraform.tfvars` and set the values of the variables defined at `variables.tf`
#
# terraform init      Initialize a Terraform working directory
# terraform validate  Validates the Terraform files
# terraform fmt       Rewrites config files to canonical format
# terraform plan      Generate and show an execution plan
# terraform apply     Builds or changes infrastructure
# terraform destroy   Destroy Terraform-managed infrastructure
#
# Go to the EMR console, and use "Add step" in the "Steps" tab to
# run the MapReduce streaming jobs
provider "aws" {
  region = "${var.region}"
}

resource "aws_security_group" "student_ami_sg" {
  # inbound internet access
  ingress {
    from_port = 22
    to_port   = 22
    protocol  = "tcp"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  ingress {
    from_port = 80
    to_port   = 80
    protocol  = "tcp"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  # outbound internet access
  # allowed: any traffic from anywhere
  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  tags = {
    Name = "Cloud Computing Workspace"
  }
}

resource "aws_security_group" "emr_master" {
  name        = "emr_master"
  description = "emr_master"

  # Instruct Terraform to revoke all of the Security Groups attached
  # ingress and egress rules before deleting the rule itself.
  # This is normally not needed, however certain AWS services such as
  # Elastic Map Reduce may automatically add required rules to security groups
  # used with the service, and those rules may contain a cyclic dependency that
  # prevent the security groups from being destroyed without removing the dependency first.
  revoke_rules_on_delete = true

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "emr_slave" {
  name        = "emr_slave"
  description = "emr_slave"

  # Instruct Terraform to revoke all of the Security Groups attached
  # ingress and egress rules before deleting the rule itself.
  # This is normally not needed, however certain AWS services such as
  # Elastic Map Reduce may automatically add required rules to security groups
  # used with the service, and those rules may contain a cyclic dependency that
  # prevent the security groups from being destroyed without removing the dependency first.
  revoke_rules_on_delete = true

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group_rule" "allow_all_traffic_from_master_to_slave" {
  type                     = "ingress"
  from_port                = 0
  to_port                  = 0
  protocol                 = "-1"
  security_group_id        = "${aws_security_group.emr_slave.id}"
  source_security_group_id = "${aws_security_group.emr_master.id}"
}

resource "aws_security_group_rule" "allow_all_traffic_from_slave_to_master" {
  type                     = "ingress"
  from_port                = 0
  to_port                  = 0
  protocol                 = "-1"
  security_group_id        = "${aws_security_group.emr_master.id}"
  source_security_group_id = "${aws_security_group.emr_slave.id}"
}

resource "aws_instance" "student_on_demand_instance" {
  ami           = "${var.workspace_ami}"
  instance_type = "${var.workspace_instance_type}"
  key_name      = "${var.key_name}"

  security_groups = [
    # note that name is used, other than id
    "${aws_security_group.student_ami_sg.name}",
    "${aws_security_group.emr_master.name}",
    "${aws_security_group.emr_slave.name}"
  ]

  tags = {
    Name    = "Cloud Computing Workspace Machine"
    Project = "4.3"
  }
}

resource "random_id" "name" {
  byte_length = 2
}

resource "aws_s3_bucket" "logs_bucket" {
  bucket = "aws-logs-${var.project_tag}-${random_id.name.hex}-${var.region}"
  region = "${var.region}"

  # Indicates all objects should be deleted from the bucket
  # so that the bucket can be destroyed without error.
  # These objects are not recoverable.
  force_destroy = true

  tags = {
    Project = "${var.project_tag}"
  }
}

resource "aws_emr_cluster" "emr-test-cluster" {
  name          = "${var.emr_cluster_name}"
  release_label = "${var.cluster_release_label}"

  applications = [
    "Hadoop",
    "ZooKeeper",
  ]

  ec2_attributes {
    # m4.large can only be used in a VPC and a Subnet is required
    subnet_id = "${aws_default_subnet.us-east-1-default-subnet.id}"

    # the key pair name
    key_name                          = "${var.key_name}"
    instance_profile                  = "EMR_EC2_DefaultRole"
    emr_managed_master_security_group = "${aws_security_group.emr_master.id}"
    emr_managed_slave_security_group  = "${aws_security_group.emr_slave.id}"
  }

  # so that you can add steps to run other jobs later
  keep_job_flow_alive_when_no_steps = true

  master_instance_group {
    instance_type  = "${var.master_node_instance_type}"
    instance_count = "${var.master_node_instance_count}"

    ebs_config {
      size                 = "50"
      type                 = "gp2"
      volumes_per_instance = 1
    }

    bid_price = "${var.master_node_bid_price}"
  }

  core_instance_group {
    instance_type  = "${var.core_node_instance_type}"
    instance_count = "${var.core_node_instance_count}"

    ebs_config {
      size                 = "50"
      type                 = "gp2"
      volumes_per_instance = 1
    }

    bid_price = "${var.core_node_bid_price}"
  }

  tags = {
    Project = "${var.project_tag}"
  }

  service_role = "EMR_DefaultRole"

  # log_uri that stores EMR logs
  log_uri = "s3://${aws_s3_bucket.logs_bucket.id}"

  configurations = "${var.config}"

  # default step to debug hadoop
  step {
    action_on_failure = "TERMINATE_CLUSTER"
    name              = "Setup Hadoop Debugging"

    hadoop_jar_step {
      jar = "command-runner.jar"

      args = [
        "state-pusher-script",
      ]
    }
  }

  # Ignore outside changes to running cluster steps
  lifecycle {
    ignore_changes = [
      "step",
    ]
  }
}

# aws_default_subnet provides a resource to manage a default AWS VPC subnet in
# the current region.
# The aws_default_subnet behaves differently from normal resources, in that
# Terraform does not create this resource, but instead "adopts" it into management.
# Terraform will not destroy the subnet during `terraform destory`.
resource "aws_default_subnet" "us-east-1-default-subnet" {
  availability_zone = "${var.zone}"
}
